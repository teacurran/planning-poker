package com.scrumpoker.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.room.Room;
import com.scrumpoker.domain.room.RoomNotFoundException;
import com.scrumpoker.domain.room.RoomService;
import com.scrumpoker.security.JwtClaims;
import com.scrumpoker.security.JwtTokenService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.common.annotation.Blocking;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket handler for real-time room communication.
 * <p>
 * Implements the Planning Poker WebSocket Protocol for room connections with:
 * <ul>
 *   <li>JWT-based authentication on handshake (token from query parameter)</li>
 *   <li>Connection lifecycle management (onOpen, onClose, onMessage, onError)</li>
 *   <li>Heartbeat protocol (ping/pong every 30 seconds, 60 second timeout)</li>
 *   <li>Thread-safe connection registry per room</li>
 *   <li>Event broadcasting to room participants</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Endpoint:</strong> {@code wss://api.../ws/room/{roomId}?token={jwt}}
 * </p>
 * <p>
 * <strong>Authentication:</strong> JWT token validated on connection, user identity
 * stored in session user properties.
 * </p>
 * <p>
 * <strong>Protocol Requirements:</strong> Client MUST send {@code room.join.v1} message
 * within 10 seconds of connection, otherwise connection is closed with code 4008.
 * </p>
 *
 * @see WebSocketMessage
 * @see ConnectionRegistry
 * @see <a href="api/websocket-protocol.md">WebSocket Protocol Specification</a>
 */
@ServerEndpoint("/ws/room/{roomId}")
@ApplicationScoped
public class RoomWebSocketHandler {

    private static final String USER_ID_KEY = "userId";
    private static final String ROOM_ID_KEY = "roomId";
    private static final String JOIN_TIMEOUT_KEY = "joinTimeout";
    private static final int JOIN_TIMEOUT_SECONDS = 10;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 60;

    /**
     * Tracks pending join timeouts per session.
     * Session must send room.join.v1 within 10 seconds or be disconnected.
     */
    private final ConcurrentHashMap<String, Long> pendingJoins = new ConcurrentHashMap<>();

    /**
     * Maps session ID to WebSocket Session object for join timeout enforcement.
     * Allows the scheduled task to look up and close sessions that exceed the join timeout.
     */
    private final ConcurrentHashMap<String, Session> sessionIdToSession = new ConcurrentHashMap<>();

    @Inject
    ConnectionRegistry connectionRegistry;

    @Inject
    JwtTokenService jwtTokenService;

    @Inject
    RoomService roomService;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MessageRouter messageRouter;

    @Inject
    io.vertx.core.Vertx vertx;

    /**
     * Called when a WebSocket connection is established.
     * <p>
     * Process:
     * <ol>
     *   <li>Extract JWT token from query parameter {@code ?token={jwt}}</li>
     *   <li>Validate JWT token and extract user claims (async)</li>
     *   <li>Validate room exists and is not deleted (async)</li>
     *   <li>Register connection in connection registry</li>
     *   <li>Schedule join timeout (client must send room.join.v1 within 10s)</li>
     * </ol>
     * </p>
     * <p>
     * Note: Token and room validation are performed reactively to avoid blocking
     * the Vert.x event loop. If validation fails, the connection is closed asynchronously.
     * </p>
     *
     * @param session The WebSocket session
     * @param roomId The room ID from path parameter
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        Log.infof("WebSocket connection attempt: session %s, room %s", session.getId(), roomId);

        // Extract JWT token from query parameter
        String token = extractTokenFromQuery(session);
        if (token == null || token.isBlank()) {
            closeWithError(session, 4000, "UNAUTHORIZED", "Missing or invalid JWT token");
            return;
        }

        // Validate token and room reactively (async, non-blocking)
        jwtTokenService.validateAccessToken(token)
                .onItem().transformToUni(claims -> {
                    // Token valid, now validate room
                    return roomService.findById(roomId)
                            .onItem().transform(room -> new ValidationResult(claims, room));
                })
                .subscribe().with(
                        validationResult -> {
                            // Success: both token and room are valid
                            JwtClaims claims = validationResult.claims;

                            // Store user ID and room ID in session properties
                            session.getUserProperties().put(USER_ID_KEY, claims.userId().toString());
                            session.getUserProperties().put(ROOM_ID_KEY, roomId);

                            // Track session for join timeout enforcement
                            sessionIdToSession.put(session.getId(), session);

                            // Register connection in registry
                            connectionRegistry.addConnection(roomId, session);

                            // Schedule join timeout (client must send room.join.v1 within 10 seconds)
                            scheduleJoinTimeout(session);

                            Log.infof("WebSocket connection established: user %s, room %s, session %s",
                                    claims.userId(), roomId, session.getId());
                        },
                        failure -> {
                            // Failure: either token invalid or room not found
                            Log.errorf(failure, "Failed to establish WebSocket connection for session %s", session.getId());

                            if (failure instanceof RoomNotFoundException) {
                                closeWithError(session, 4001, "ROOM_NOT_FOUND",
                                        "Room does not exist or has been deleted");
                            } else {
                                closeWithError(session, 4000, "UNAUTHORIZED", "Invalid or expired JWT token");
                            }
                        }
                );
    }

    /**
     * Internal helper class to pass validation results through reactive chain.
     */
    private record ValidationResult(JwtClaims claims, Room room) {
    }

    /**
     * Called when a WebSocket connection is closed.
     * <p>
     * Process:
     * <ol>
     *   <li>Remove connection from registry</li>
     *   <li>Broadcast {@code room.participant_left.v1} event to remaining participants</li>
     *   <li>Clean up session tracking data</li>
     * </ol>
     * </p>
     *
     * @param session The WebSocket session
     * @param closeReason The close reason
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        String sessionId = session.getId();
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String roomId = connectionRegistry.removeConnection(session);

        // Clean up pending join timeout
        pendingJoins.remove(sessionId);

        // Clean up session tracking
        sessionIdToSession.remove(sessionId);

        if (roomId != null && userId != null) {
            // Broadcast participant_left event to remaining participants
            String reason = mapCloseReasonToString(closeReason);
            WebSocketMessage leftMessage = WebSocketMessage.createParticipantLeft(
                    userId,
                    Instant.now().toString(),
                    reason
            );
            connectionRegistry.broadcastToRoom(roomId, leftMessage);

            Log.infof("WebSocket connection closed: user %s, room %s, session %s, reason: %s (%s)",
                    userId, roomId, sessionId, closeReason.getReasonPhrase(), closeReason.getCloseCode());
        } else {
            Log.infof("WebSocket connection closed: session %s (not fully initialized), reason: %s",
                    sessionId, closeReason.getReasonPhrase());
        }
    }

    /**
     * Called when a text message is received from the client.
     * <p>
     * This method parses the JSON message envelope and routes it to the appropriate
     * message handler. For this task (I4.T1), message handlers are placeholder implementations.
     * Full message handling will be implemented in Task I4.T4.
     * </p>
     *
     * @param session The WebSocket session
     * @param messageText The received message (JSON)
     */
    @OnMessage
    public void onMessage(Session session, String messageText) {
        String sessionId = session.getId();
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String roomId = (String) session.getUserProperties().get(ROOM_ID_KEY);

        Log.debugf("Received message from session %s: %s", sessionId, messageText);

        try {
            // Parse message envelope
            WebSocketMessage message = objectMapper.readValue(messageText, WebSocketMessage.class);

            // Validate message structure
            if (message.getType() == null || message.getType().isBlank()) {
                sendError(session, message.getRequestId(), 4004, "VALIDATION_ERROR",
                        "Message type is required");
                return;
            }

            if (message.getRequestId() == null || message.getRequestId().isBlank()) {
                sendError(session, UUID.randomUUID().toString(), 4004, "VALIDATION_ERROR",
                        "Request ID is required");
                return;
            }

            // Handle room.join.v1 message (cancel join timeout)
            if ("room.join.v1".equals(message.getType())) {
                handleRoomJoin(session, message);
                return;
            }

            // Handle room.leave.v1 message (graceful disconnect)
            if ("room.leave.v1".equals(message.getType())) {
                handleRoomLeave(session, message);
                return;
            }

            // Route to message handlers via MessageRouter
            // Fire and forget - the router handles its own context management
            messageRouter.route(session, message, userId, roomId)
                    .subscribe().with(
                            success -> Log.debugf("Message handled: type=%s, requestId=%s",
                                    message.getType(), message.getRequestId()),
                            failure -> Log.errorf(failure, "Failed to handle message: type=%s, requestId=%s",
                                    message.getType(), message.getRequestId())
                    );

        } catch (Exception e) {
            Log.errorf(e, "Failed to process message from session %s: %s", sessionId, messageText);
            sendError(session, UUID.randomUUID().toString(), 4004, "VALIDATION_ERROR",
                    "Invalid message format: " + e.getMessage());
        }
    }

    /**
     * Called when a pong frame is received (heartbeat response).
     * <p>
     * Updates the last pong timestamp in the connection registry for heartbeat tracking.
     * </p>
     *
     * @param session The WebSocket session
     * @param pongMessage The pong message payload
     */
    @OnMessage
    public void onPong(Session session, PongMessage pongMessage) {
        connectionRegistry.updateLastPong(session);
        Log.debugf("Pong received from session %s", session.getId());
    }

    /**
     * Called when an error occurs on the WebSocket connection.
     * <p>
     * Logs the error and closes the connection gracefully.
     * </p>
     *
     * @param session The WebSocket session
     * @param throwable The error that occurred
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        String sessionId = session.getId();
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String roomId = (String) session.getUserProperties().get(ROOM_ID_KEY);

        Log.errorf(throwable, "WebSocket error: session %s, user %s, room %s",
                sessionId, userId, roomId);

        try {
            if (session.isOpen()) {
                session.close(new CloseReason(
                        CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                        "Internal error: " + throwable.getMessage()
                ));
            }
        } catch (IOException e) {
            Log.errorf(e, "Failed to close session %s after error", sessionId);
        }
    }

    /**
     * Scheduled task that sends ping frames to all active connections every 30 seconds.
     * <p>
     * This implements the heartbeat protocol to detect stale connections.
     * Server sends ping, expects pong response within 60 seconds.
     * </p>
     */
    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void sendHeartbeatPings() {
        int totalConnections = connectionRegistry.getTotalConnectionCount();
        if (totalConnections == 0) {
            return; // No connections, skip
        }

        Log.debugf("Sending heartbeat pings to %d active connections", totalConnections);

        int successCount = 0;
        int failureCount = 0;

        // Get all active sessions across all rooms
        Set<Session> allSessions = connectionRegistry.getAllSessions();

        for (Session session : allSessions) {
            try {
                if (session.isOpen()) {
                    // Send WebSocket ping frame (empty payload)
                    session.getAsyncRemote().sendPing(ByteBuffer.allocate(0));
                    successCount++;
                } else {
                    Log.debugf("Session %s is closed, skipping ping", session.getId());
                    failureCount++;
                }
            } catch (Exception e) {
                Log.warnf(e, "Failed to send ping to session %s", session.getId());
                failureCount++;
            }
        }

        Log.debugf("Heartbeat ping cycle completed: %d succeeded, %d failed", successCount, failureCount);
    }

    /**
     * Scheduled task that cleans up stale connections (no pong received within 60 seconds).
     * <p>
     * Runs every 60 seconds, checks for sessions that haven't responded to ping,
     * and closes them with a timeout reason.
     * </p>
     */
    @Scheduled(every = "60s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void cleanupStaleConnections() {
        Set<Session> staleSessions = connectionRegistry.getStaleSessions(HEARTBEAT_TIMEOUT_SECONDS);

        if (staleSessions.isEmpty()) {
            return;
        }

        Log.infof("Cleaning up %d stale connections (no pong within %d seconds)",
                staleSessions.size(), HEARTBEAT_TIMEOUT_SECONDS);

        for (Session session : staleSessions) {
            try {
                String userId = (String) session.getUserProperties().get(USER_ID_KEY);
                String roomId = (String) session.getUserProperties().get(ROOM_ID_KEY);

                Log.warnf("Closing stale connection: user %s, room %s, session %s",
                        userId, roomId, session.getId());

                if (session.isOpen()) {
                    session.close(new CloseReason(
                            CloseReason.CloseCodes.NORMAL_CLOSURE,
                            "Connection timeout - no heartbeat"
                    ));
                }
            } catch (Exception e) {
                Log.errorf(e, "Failed to close stale session %s", session.getId());
            }
        }
    }

    /**
     * Scheduled task that enforces join timeout (client must send room.join.v1 within 10 seconds).
     * <p>
     * Runs every 5 seconds, checks for sessions that exceeded the join timeout,
     * and closes them with code 4008 (POLICY_VIOLATION).
     * </p>
     */
    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void enforceJoinTimeout() {
        long now = System.currentTimeMillis();

        pendingJoins.entrySet().removeIf(entry -> {
            String sessionId = entry.getKey();
            long deadline = entry.getValue();

            if (now > deadline) {
                // Look up session and close it with POLICY_VIOLATION code
                Session session = sessionIdToSession.get(sessionId);

                if (session != null && session.isOpen()) {
                    try {
                        Log.warnf("Session %s exceeded join timeout (10 seconds), closing with code 4008 (POLICY_VIOLATION)", sessionId);

                        // Custom close code 4008 for POLICY_VIOLATION
                        CloseReason closeReason = new CloseReason(
                                new CloseReason.CloseCode() {
                                    @Override
                                    public int getCode() {
                                        return 4008;
                                    }
                                },
                                "POLICY_VIOLATION: Failed to send room.join.v1 within 10 seconds"
                        );

                        session.close(closeReason);
                    } catch (Exception e) {
                        Log.errorf(e, "Failed to close session %s for join timeout", sessionId);
                    }
                }

                return true; // Remove from pending joins
            }

            return false;
        });
    }

    // Helper Methods

    /**
     * Extracts JWT token from query parameter {@code ?token={jwt}}.
     *
     * @param session The WebSocket session
     * @return The JWT token, or null if not found
     */
    private String extractTokenFromQuery(Session session) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> tokens = params.get("token");

        if (tokens != null && !tokens.isEmpty()) {
            return tokens.get(0);
        }

        return null;
    }

    /**
     * Validates JWT token and returns claims.
     *
     * @param token The JWT token
     * @return JwtClaims if valid, null otherwise
     */
    private JwtClaims validateToken(String token) {
        try {
            return jwtTokenService.validateAccessToken(token)
                    .await().atMost(java.time.Duration.ofSeconds(5));
        } catch (Exception e) {
            Log.errorf(e, "JWT token validation failed: %s", e.getMessage());
            return null;
        }
    }

    /**
     * Validates that the room exists and is not deleted.
     *
     * @param roomId The room ID
     * @return The Room entity if valid, null otherwise
     */
    private Room validateRoomExists(String roomId) {
        try {
            return roomService.findById(roomId)
                    .await().atMost(java.time.Duration.ofSeconds(5));
        } catch (RoomNotFoundException e) {
            Log.warnf("Room not found: %s", roomId);
            return null;
        } catch (Exception e) {
            Log.errorf(e, "Failed to validate room %s: %s", roomId, e.getMessage());
            return null;
        }
    }

    /**
     * Schedules a join timeout for the session.
     * Client must send room.join.v1 within 10 seconds or connection is closed.
     *
     * @param session The WebSocket session
     */
    private void scheduleJoinTimeout(Session session) {
        long deadline = System.currentTimeMillis() + (JOIN_TIMEOUT_SECONDS * 1000L);
        pendingJoins.put(session.getId(), deadline);
    }

    /**
     * Cancels the join timeout for a session (called when room.join.v1 is received).
     *
     * @param session The WebSocket session
     */
    private void cancelJoinTimeout(Session session) {
        pendingJoins.remove(session.getId());
        Log.debugf("Join timeout cancelled for session %s", session.getId());
    }

    /**
     * Handles the room.join.v1 message.
     *
     * @param session The WebSocket session
     * @param message The join message
     */
    private void handleRoomJoin(Session session, WebSocketMessage message) {
        // Cancel join timeout
        cancelJoinTimeout(session);

        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String roomId = (String) session.getUserProperties().get(ROOM_ID_KEY);

        Log.infof("Room join received: user %s, room %s, session %s",
                userId, roomId, session.getId());

        // Extract displayName from payload (or use default)
        String displayName = "Anonymous";
        if (message.getPayload() != null && message.getPayload().containsKey("displayName")) {
            Object displayNameObj = message.getPayload().get("displayName");
            if (displayNameObj instanceof String) {
                displayName = (String) displayNameObj;
            }
        }

        // Determine participant role (default to VOTER for now)
        // Full role assignment logic will be implemented in Task I4.T4
        String role = "VOTER";

        // Broadcast participant_joined event to all participants in the room
        String connectedAt = Instant.now().toString();
        WebSocketMessage joinedMessage = WebSocketMessage.createParticipantJoined(
                userId,
                displayName,
                role,
                connectedAt
        );

        connectionRegistry.broadcastToRoom(roomId, joinedMessage);

        Log.infof("Participant joined event broadcasted to room %s: user %s (%s, %s)",
                roomId, userId, displayName, role);

        // TODO (I4.T4): Implement full room join logic:
        // 1. Create/update RoomParticipant record in database
        // 2. Send room.state.v1 (initial state snapshot) to newly connected client
    }

    /**
     * Handles the room.leave.v1 message (graceful disconnect).
     *
     * @param session The WebSocket session
     * @param message The leave message
     */
    private void handleRoomLeave(Session session, WebSocketMessage message) {
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String roomId = (String) session.getUserProperties().get(ROOM_ID_KEY);

        Log.infof("Room leave received: user %s, room %s, session %s",
                userId, roomId, session.getId());

        try {
            // Close connection gracefully
            session.close(new CloseReason(
                    CloseReason.CloseCodes.NORMAL_CLOSURE,
                    "User-initiated leave"
            ));
        } catch (IOException e) {
            Log.errorf(e, "Failed to close session %s after leave", session.getId());
        }
    }

    /**
     * Closes a WebSocket session with an error code and message.
     *
     * @param session The WebSocket session
     * @param code The error code (4000-4999 range)
     * @param error The error type
     * @param message The error message
     */
    private void closeWithError(Session session, int code, String error, String message) {
        try {
            // Send error message before closing
            sendError(session, UUID.randomUUID().toString(), code, error, message);

            // Close connection
            CloseReason.CloseCode closeCode = CloseReason.CloseCodes.PROTOCOL_ERROR;
            session.close(new CloseReason(closeCode, error));

            Log.infof("Closed session %s with error %d: %s", session.getId(), code, message);
        } catch (Exception e) {
            Log.errorf(e, "Failed to close session %s with error", session.getId());
        }
    }

    /**
     * Sends an error message to a WebSocket session.
     *
     * @param session The WebSocket session
     * @param requestId The request ID that triggered the error
     * @param code The error code
     * @param error The error type
     * @param message The error message
     */
    private void sendError(Session session, String requestId, int code, String error, String message) {
        WebSocketMessage errorMessage = WebSocketMessage.createError(
                requestId, code, error, message, Instant.now().toString()
        );
        connectionRegistry.sendToSession(session, errorMessage);
    }

    /**
     * Maps WebSocket close reason to protocol-defined reason string.
     *
     * @param closeReason The WebSocket close reason
     * @return Protocol reason string (user_initiated, timeout, kicked)
     */
    private String mapCloseReasonToString(CloseReason closeReason) {
        if (closeReason.getReasonPhrase().contains("timeout") ||
                closeReason.getReasonPhrase().contains("heartbeat")) {
            return "timeout";
        }

        if (closeReason.getCloseCode() == CloseReason.CloseCodes.NORMAL_CLOSURE) {
            return "user_initiated";
        }

        return "user_initiated"; // Default
    }
}
