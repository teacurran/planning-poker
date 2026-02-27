package com.scrumpoker.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.room.*;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.domain.user.UserService;
import com.scrumpoker.logging.LoggingConstants;
import com.scrumpoker.repository.RoomParticipantRepository;
import com.scrumpoker.repository.RoundRepository;
import com.scrumpoker.repository.VoteRepository;
import com.scrumpoker.security.OidcTokenVerifier;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * WebSocket handler for real-time room communication.
 * Validates OIDC tokens from Keycloak on handshake and manages
 * the full room join lifecycle including state snapshots.
 */
@ServerEndpoint("/ws/room/{roomId}")
@ApplicationScoped
public class RoomWebSocketHandler {

    private static final String USER_ID_KEY = "userId";
    private static final String ROOM_ID_KEY = "roomId";
    private static final String DISPLAY_NAME_KEY = "displayName";
    private static final String EMAIL_KEY = "email";
    private static final String OIDC_SUB_KEY = "oidcSub";
    private static final int JOIN_TIMEOUT_SECONDS = 10;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int HEARTBEAT_TIMEOUT_SECONDS = 45;
    private static final CloseReason.CloseCode CLOSE_CODE_UNAUTHORIZED = () -> 4401;

    private final ConcurrentHashMap<String, Long> pendingJoins = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Session> sessionIdToSession = new ConcurrentHashMap<>();

    @Inject
    ConnectionRegistry connectionRegistry;

    @Inject
    OidcTokenVerifier oidcTokenVerifier;

    @Inject
    UserService userService;

    @Inject
    RoomService roomService;

    @Inject
    RoomParticipantRepository roomParticipantRepository;

    @Inject
    RoundRepository roundRepository;

    @Inject
    VoteRepository voteRepository;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MessageRouter messageRouter;

    @Inject
    io.vertx.core.Vertx vertx;

    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        String correlationId = UUID.randomUUID().toString();
        session.getUserProperties().put(LoggingConstants.WS_CORRELATION_ID_PROPERTY, correlationId);

        MDC.put(LoggingConstants.CORRELATION_ID, correlationId);
        MDC.put(LoggingConstants.ROOM_ID, roomId);
        Log.infof("WebSocket connection attempt: session %s, room %s", session.getId(), roomId);
        MDC.clear();

        String token = extractTokenFromQuery(session);
        if (token == null || token.isBlank()) {
            Log.warnf("Rejecting WebSocket connection for room %s due to missing token", roomId);
            closeUnauthorized(session, "Missing authentication token");
            return;
        }

        // Verify OIDC token and validate room exists
        oidcTokenVerifier.verifyToken(token)
                .onItem().transformToUni(oidcUser -> {
                    return roomService.findById(roomId)
                            .onItem().transform(room -> new ValidationResult(oidcUser, room));
                })
                .subscribe().with(
                        result -> {
                            OidcTokenVerifier.OidcUser oidcUser = result.oidcUser;

                            MDC.put(LoggingConstants.CORRELATION_ID, correlationId);
                            MDC.put(LoggingConstants.ROOM_ID, roomId);

                            try {
                                // Store OIDC claims in session for use in handleRoomJoin
                                session.getUserProperties().put(OIDC_SUB_KEY, oidcUser.sub());
                                session.getUserProperties().put(DISPLAY_NAME_KEY, oidcUser.preferredUsername());
                                session.getUserProperties().put(EMAIL_KEY, oidcUser.email());
                                session.getUserProperties().put(ROOM_ID_KEY, roomId);

                                sessionIdToSession.put(session.getId(), session);
                                connectionRegistry.addConnection(roomId, session);
                                scheduleJoinTimeout(session);

                                Log.infof("Authenticated WebSocket connection: sub=%s, room=%s, session=%s",
                                        oidcUser.sub(), roomId, session.getId());
                            } finally {
                                MDC.clear();
                            }
                        },
                        failure -> {
                            MDC.put(LoggingConstants.CORRELATION_ID, correlationId);
                            MDC.put(LoggingConstants.ROOM_ID, roomId);
                            try {
                                Log.errorf(failure, "Failed to establish WebSocket for session %s", session.getId());
                                if (failure instanceof RoomNotFoundException) {
                                    closeWithError(session, 4001, "ROOM_NOT_FOUND",
                                            "Room does not exist or has been deleted");
                                } else {
                                    closeUnauthorized(session, "Invalid or expired token");
                                }
                            } finally {
                                MDC.clear();
                            }
                        }
                );
    }

    private record ValidationResult(OidcTokenVerifier.OidcUser oidcUser, Room room) {}

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        String sessionId = session.getId();
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String oidcSub = (String) session.getUserProperties().get(OIDC_SUB_KEY);
        String roomId = connectionRegistry.removeConnection(session);

        pendingJoins.remove(sessionId);
        sessionIdToSession.remove(sessionId);

        if (roomId != null && (userId != null || oidcSub != null)) {
            String participantId = userId != null ? userId : oidcSub;

            // Update disconnected_at in database
            updateParticipantDisconnectedAt(roomId, participantId);

            // Broadcast participant_left
            String reason = mapCloseReasonToString(closeReason);
            WebSocketMessage leftMessage = WebSocketMessage.createParticipantLeft(
                    participantId, Instant.now().toString(), reason);
            connectionRegistry.broadcastToRoom(roomId, leftMessage);

            Log.infof("WebSocket closed: user %s, room %s, reason: %s",
                    participantId, roomId, closeReason.getReasonPhrase());
        } else {
            Log.infof("WebSocket closed: session %s (not fully initialized)", sessionId);
        }
    }

    @OnMessage
    public void onMessage(Session session, String messageText) {
        String sessionId = session.getId();
        String userId = (String) session.getUserProperties().get(USER_ID_KEY);
        String roomId = (String) session.getUserProperties().get(ROOM_ID_KEY);

        // Update last activity for heartbeat tracking
        connectionRegistry.updateLastPong(session);

        try {
            WebSocketMessage message = objectMapper.readValue(messageText, WebSocketMessage.class);

            if (message.getType() == null || message.getType().isBlank()) {
                sendError(session, message.getRequestId(), 4004, "VALIDATION_ERROR",
                        "Message type is required");
                return;
            }

            // Handle ping.v1 - application-level heartbeat
            if ("ping.v1".equals(message.getType())) {
                WebSocketMessage pong = WebSocketMessage.create("pong.v1", new HashMap<>());
                connectionRegistry.sendToSession(session, pong);
                return;
            }

            if ("room.join.v1".equals(message.getType())) {
                handleRoomJoin(session, message);
                return;
            }

            if ("room.leave.v1".equals(message.getType())) {
                handleRoomLeave(session, message);
                return;
            }

            // Route to message handlers in a Vert.x context for Hibernate Reactive
            io.vertx.core.Context duplicatedContext = io.vertx.core.impl.VertxInternal.class.cast(vertx)
                    .getOrCreateContext().duplicate();
            io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle.setContextSafe(duplicatedContext, true);

            duplicatedContext.runOnContext(v -> {
                messageRouter.route(session, message, userId, roomId)
                        .subscribe().with(
                                success -> Log.debugf("Message handled: type=%s", message.getType()),
                                failure -> Log.errorf(failure, "Failed to handle message: type=%s", message.getType())
                        );
            });
        } catch (Exception e) {
            Log.errorf(e, "Failed to process message from session %s", sessionId);
            sendError(session, UUID.randomUUID().toString(), 4004, "VALIDATION_ERROR",
                    "Invalid message format: " + e.getMessage());
        }
    }

    @OnMessage
    public void onPong(Session session, PongMessage pongMessage) {
        connectionRegistry.updateLastPong(session);
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        Log.errorf(throwable, "WebSocket error: session %s", session.getId());
        try {
            if (session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                        "Internal error"));
            }
        } catch (IOException e) {
            Log.errorf(e, "Failed to close session %s after error", session.getId());
        }
    }

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void sendHeartbeatPings() {
        int totalConnections = connectionRegistry.getTotalConnectionCount();
        if (totalConnections == 0) return;

        Set<Session> allSessions = connectionRegistry.getAllSessions();
        for (Session session : allSessions) {
            try {
                if (session.isOpen()) {
                    session.getAsyncRemote().sendPing(ByteBuffer.allocate(0));
                }
            } catch (Exception e) {
                Log.warnf(e, "Failed to send ping to session %s", session.getId());
            }
        }
    }

    @Scheduled(every = "15s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void cleanupStaleConnections() {
        Set<Session> staleSessions = connectionRegistry.getStaleSessions(HEARTBEAT_TIMEOUT_SECONDS);
        if (staleSessions.isEmpty()) return;

        Log.infof("Cleaning up %d stale connections (no activity within %ds)",
                staleSessions.size(), HEARTBEAT_TIMEOUT_SECONDS);

        for (Session session : staleSessions) {
            try {
                if (session.isOpen()) {
                    session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE,
                            "Connection timeout - no heartbeat"));
                }
            } catch (Exception e) {
                Log.errorf(e, "Failed to close stale session %s", session.getId());
            }
        }
    }

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void enforceJoinTimeout() {
        long now = System.currentTimeMillis();
        pendingJoins.entrySet().removeIf(entry -> {
            if (now > entry.getValue()) {
                Session session = sessionIdToSession.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    try {
                        session.close(new CloseReason(() -> 4008,
                                "POLICY_VIOLATION: Failed to send room.join.v1 within 10 seconds"));
                    } catch (Exception e) {
                        Log.errorf(e, "Failed to close session %s for join timeout", entry.getKey());
                    }
                }
                return true;
            }
            return false;
        });
    }

    // ========================================
    // Room Join - Critical Implementation
    // ========================================

    private void handleRoomJoin(Session session, WebSocketMessage message) {
        cancelJoinTimeout(session);

        String oidcSub = (String) session.getUserProperties().get(OIDC_SUB_KEY);
        String oidcDisplayName = (String) session.getUserProperties().get(DISPLAY_NAME_KEY);
        String oidcEmail = (String) session.getUserProperties().get(EMAIL_KEY);
        String roomId = (String) session.getUserProperties().get(ROOM_ID_KEY);

        // Override display name from payload if provided
        String displayName = oidcDisplayName;
        if (message.getPayload() != null && message.getPayload().containsKey("displayName")) {
            Object dn = message.getPayload().get("displayName");
            if (dn instanceof String && !((String) dn).isBlank()) {
                displayName = (String) dn;
            }
        }

        String role = "VOTER";
        if (message.getPayload() != null && message.getPayload().containsKey("role")) {
            Object r = message.getPayload().get("role");
            if (r instanceof String) {
                role = (String) r;
            }
        }

        final String finalDisplayName = displayName;
        final String finalRole = role;

        Log.infof("Room join: sub=%s, room=%s, displayName=%s", oidcSub, roomId, displayName);

        // Run all DB operations in a Vert.x context for Hibernate Reactive
        io.vertx.core.Context duplicatedContext = io.vertx.core.impl.VertxInternal.class.cast(vertx)
                .getOrCreateContext().duplicate();
        io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle.setContextSafe(duplicatedContext, true);

        duplicatedContext.runOnContext(v -> {
            // 1. Find or create User from OIDC claims
            Panache.withTransaction(() ->
                userService.findOrCreateUser("keycloak", oidcSub, oidcEmail, finalDisplayName, null)
            )
            .onItem().transformToUni(user -> {
                // Store resolved userId in session
                session.getUserProperties().put(USER_ID_KEY, user.userId.toString());

                // 2. Create or update RoomParticipant
                return Panache.withTransaction(() ->
                    persistParticipant(roomId, user, finalDisplayName, finalRole)
                );
            })
            .onItem().transformToUni(participant -> {
                // 3. Build and send room.state.v1 snapshot
                return Panache.withSession(() ->
                    buildRoomState(roomId)
                );
            })
            .subscribe().with(
                stateMessage -> {
                    // Send state snapshot to the joining client
                    connectionRegistry.sendToSession(session, stateMessage);

                    String userId = (String) session.getUserProperties().get(USER_ID_KEY);

                    // Broadcast participant_joined to all OTHER participants
                    WebSocketMessage joinedMessage = WebSocketMessage.createParticipantJoined(
                            userId, finalDisplayName, finalRole, Instant.now().toString());

                    Set<Session> roomSessions = connectionRegistry.getConnectionsForRoom(roomId);
                    for (Session s : roomSessions) {
                        if (!s.getId().equals(session.getId())) {
                            connectionRegistry.sendToSession(s, joinedMessage);
                        }
                    }

                    Log.infof("Room join complete: user=%s, room=%s", userId, roomId);
                },
                failure -> {
                    Log.errorf(failure, "Failed to complete room join for sub=%s, room=%s", oidcSub, roomId);
                    sendError(session, message.getRequestId(), 4999, "INTERNAL_SERVER_ERROR",
                            "Failed to join room");
                }
            );
        });
    }

    /**
     * Persists a RoomParticipant record (upsert).
     */
    private io.smallrye.mutiny.Uni<RoomParticipant> persistParticipant(
            String roomId, User user, String displayName, String role) {

        return roomParticipantRepository.find("room.roomId = ?1 and user.userId = ?2",
                        roomId, user.userId).firstResult()
                .onItem().transformToUni(existing -> {
                    if (existing != null) {
                        // Update existing participant
                        existing.displayName = displayName;
                        existing.role = RoomRole.valueOf(role);
                        existing.connectedAt = Instant.now();
                        existing.disconnectedAt = null;
                        return roomParticipantRepository.persist(existing);
                    } else {
                        // Create new participant
                        return roomService.findById(roomId)
                                .onItem().transformToUni(room -> {
                                    RoomParticipant participant = new RoomParticipant();
                                    participant.room = room;
                                    participant.user = user;
                                    participant.displayName = displayName;
                                    participant.role = RoomRole.valueOf(role);
                                    participant.connectedAt = Instant.now();
                                    return roomParticipantRepository.persist(participant);
                                });
                    }
                });
    }

    /**
     * Builds a room.state.v1 message with full room state snapshot.
     */
    private io.smallrye.mutiny.Uni<WebSocketMessage> buildRoomState(String roomId) {
        return io.smallrye.mutiny.Uni.combine().all().unis(
                roomService.findById(roomId),
                roomParticipantRepository.findByRoomId(roomId),
                roundRepository.findLatestByRoomId(roomId)
        ).asTuple()
        .onItem().transformToUni(tuple -> {
            Room room = tuple.getItem1();
            List<RoomParticipant> participants = tuple.getItem2();
            Round currentRound = tuple.getItem3();

            // Build participants list
            Set<String> connectedUserIds = new HashSet<>();
            Set<Session> roomSessions = connectionRegistry.getConnectionsForRoom(roomId);
            for (Session s : roomSessions) {
                String uid = (String) s.getUserProperties().get(USER_ID_KEY);
                if (uid != null) connectedUserIds.add(uid);
            }

            List<Map<String, Object>> participantsList = participants.stream()
                    .map(p -> {
                        Map<String, Object> pm = new HashMap<>();
                        pm.put("participantId", p.user != null ? p.user.userId.toString() : p.anonymousId);
                        pm.put("displayName", p.displayName);
                        pm.put("role", p.role.name());
                        pm.put("connectedAt", p.connectedAt.toString());
                        pm.put("hasVoted", false);
                        boolean connected = p.user != null && connectedUserIds.contains(p.user.userId.toString());
                        pm.put("connected", connected);
                        return pm;
                    })
                    .collect(Collectors.toList());

            // Build current round info
            Map<String, Object> payload = new HashMap<>();
            payload.put("roomId", room.roomId);
            payload.put("title", room.title);
            payload.put("config", parseConfig(room.config));
            payload.put("participants", participantsList);

            if (currentRound != null && currentRound.revealedAt == null) {
                // Active unrevealed round
                Map<String, Object> roundMap = new HashMap<>();
                roundMap.put("roundId", currentRound.roundId.toString());
                roundMap.put("roundNumber", currentRound.roundNumber);
                roundMap.put("storyTitle", currentRound.storyTitle);
                roundMap.put("startedAt", currentRound.startedAt.toString());
                roundMap.put("revealed", false);
                roundMap.put("revealedAt", null);
                payload.put("currentRound", roundMap);

                // Fetch votes to check who has voted
                return voteRepository.findByRoundId(currentRound.roundId)
                        .onItem().transform(votes -> {
                            Set<String> votedParticipantIds = votes.stream()
                                    .map(v -> v.participant.participantId.toString())
                                    .collect(Collectors.toSet());

                            // Update hasVoted for each participant
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> pList = (List<Map<String, Object>>) payload.get("participants");
                            for (Map<String, Object> pm : pList) {
                                // Check by participant record ID
                                for (RoomParticipant rp : participants) {
                                    String pid = rp.user != null ? rp.user.userId.toString() : rp.anonymousId;
                                    if (pid != null && pid.equals(pm.get("participantId"))) {
                                        pm.put("hasVoted", votedParticipantIds.contains(rp.participantId.toString()));
                                        break;
                                    }
                                }
                            }

                            return WebSocketMessage.create("room.state.v1", payload);
                        });
            } else if (currentRound != null && currentRound.revealedAt != null) {
                // Revealed round - include votes
                Map<String, Object> roundMap = new HashMap<>();
                roundMap.put("roundId", currentRound.roundId.toString());
                roundMap.put("roundNumber", currentRound.roundNumber);
                roundMap.put("storyTitle", currentRound.storyTitle);
                roundMap.put("startedAt", currentRound.startedAt.toString());
                roundMap.put("revealed", true);
                roundMap.put("revealedAt", currentRound.revealedAt.toString());
                payload.put("currentRound", roundMap);

                return voteRepository.findByRoundId(currentRound.roundId)
                        .onItem().transform(votes -> {
                            List<Map<String, Object>> votesList = votes.stream()
                                    .map(v -> {
                                        Map<String, Object> vm = new HashMap<>();
                                        vm.put("participantId", v.participant.participantId.toString());
                                        vm.put("cardValue", v.cardValue);
                                        vm.put("displayName", v.participant.displayName);
                                        vm.put("votedAt", v.votedAt.toString());
                                        return vm;
                                    })
                                    .collect(Collectors.toList());
                            payload.put("votes", votesList);
                            return WebSocketMessage.create("room.state.v1", payload);
                        });
            } else {
                // No active round
                payload.put("currentRound", null);
                return io.smallrye.mutiny.Uni.createFrom().item(
                        WebSocketMessage.create("room.state.v1", payload));
            }
        });
    }

    private Object parseConfig(String configJson) {
        if (configJson == null) return new HashMap<>();
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            Log.warnf("Failed to parse room config: %s", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Updates RoomParticipant.disconnectedAt in the database.
     */
    private void updateParticipantDisconnectedAt(String roomId, String userIdStr) {
        io.vertx.core.Context duplicatedContext = io.vertx.core.impl.VertxInternal.class.cast(vertx)
                .getOrCreateContext().duplicate();
        io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle.setContextSafe(duplicatedContext, true);

        duplicatedContext.runOnContext(v -> {
            try {
                UUID userId = UUID.fromString(userIdStr);
                Panache.withTransaction(() ->
                    roomParticipantRepository.find("room.roomId = ?1 and user.userId = ?2", roomId, userId)
                            .firstResult()
                            .onItem().ifNotNull().invoke(participant -> {
                                participant.disconnectedAt = Instant.now();
                            })
                ).subscribe().with(
                        result -> Log.debugf("Updated disconnectedAt for user %s in room %s", userIdStr, roomId),
                        failure -> Log.errorf(failure, "Failed to update disconnectedAt for user %s", userIdStr)
                );
            } catch (IllegalArgumentException e) {
                // Not a UUID (e.g., OIDC sub that hasn't been resolved yet)
                Log.debugf("Skipping disconnect update for non-UUID identifier: %s", userIdStr);
            }
        });
    }

    // ========================================
    // Helper Methods
    // ========================================

    private String extractTokenFromQuery(Session session) {
        Map<String, List<String>> params = session.getRequestParameterMap();
        List<String> tokens = params.get("token");
        return (tokens != null && !tokens.isEmpty()) ? tokens.get(0) : null;
    }

    private void scheduleJoinTimeout(Session session) {
        pendingJoins.put(session.getId(), System.currentTimeMillis() + (JOIN_TIMEOUT_SECONDS * 1000L));
    }

    private void cancelJoinTimeout(Session session) {
        pendingJoins.remove(session.getId());
    }

    private void handleRoomLeave(Session session, WebSocketMessage message) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "User-initiated leave"));
        } catch (IOException e) {
            Log.errorf(e, "Failed to close session %s after leave", session.getId());
        }
    }

    private void closeWithError(Session session, int code, String error, String message) {
        closeWithError(session, code, error, message, CloseReason.CloseCodes.PROTOCOL_ERROR);
    }

    private void closeWithError(Session session, int code, String error, String message,
                                CloseReason.CloseCode closeCode) {
        try {
            sendError(session, UUID.randomUUID().toString(), code, error, message);
            session.close(new CloseReason(closeCode, error));
        } catch (Exception e) {
            Log.errorf(e, "Failed to close session %s with error", session.getId());
        }
    }

    private void closeUnauthorized(Session session, String message) {
        closeWithError(session, 401, "UNAUTHORIZED", message, CLOSE_CODE_UNAUTHORIZED);
    }

    private void sendError(Session session, String requestId, int code, String error, String message) {
        WebSocketMessage errorMessage = WebSocketMessage.createError(
                requestId, code, error, message, Instant.now().toString());
        connectionRegistry.sendToSession(session, errorMessage);
    }

    private String mapCloseReasonToString(CloseReason closeReason) {
        if (closeReason.getReasonPhrase() != null &&
                (closeReason.getReasonPhrase().contains("timeout") ||
                 closeReason.getReasonPhrase().contains("heartbeat"))) {
            return "timeout";
        }
        return "user_initiated";
    }
}
