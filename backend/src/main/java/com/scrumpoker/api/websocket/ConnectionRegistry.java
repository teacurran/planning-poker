package com.scrumpoker.api.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.event.RoomEventSubscriber;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry for managing active WebSocket connections per room.
 * <p>
 * This registry tracks all active WebSocket sessions organized by room ID,
 * provides methods for adding/removing connections, and supports broadcasting
 * messages to all participants in a room.
 * </p>
 * <p>
 * <strong>Thread Safety:</strong> Uses ConcurrentHashMap and thread-safe sets
 * to support concurrent connection/disconnection events from multiple threads.
 * </p>
 */
@ApplicationScoped
public class ConnectionRegistry {

    /**
     * Map of roomId -> Set of active WebSocket sessions.
     * Uses ConcurrentHashMap.newKeySet() for thread-safe sets.
     */
    private final ConcurrentHashMap<String, Set<Session>> roomConnections;

    /**
     * Map of Session -> last pong received timestamp for heartbeat tracking.
     */
    private final ConcurrentHashMap<Session, Instant> lastPongReceived;

    /**
     * Map of Session -> roomId for reverse lookup.
     */
    private final ConcurrentHashMap<Session, String> sessionToRoom;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RoomEventSubscriber eventSubscriber;

    public ConnectionRegistry() {
        this.roomConnections = new ConcurrentHashMap<>();
        this.lastPongReceived = new ConcurrentHashMap<>();
        this.sessionToRoom = new ConcurrentHashMap<>();
    }

    /**
     * Adds a WebSocket connection to the registry for the specified room.
     * <p>
     * This method is thread-safe and can be called concurrently from multiple
     * WebSocket connection threads.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param session The WebSocket session to register
     */
    public void addConnection(String roomId, Session session) {
        // Create thread-safe set if room doesn't exist
        Set<Session> sessions = roomConnections.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        sessions.add(session);

        // Track session to room mapping for reverse lookup
        sessionToRoom.put(session, roomId);

        // Initialize heartbeat timestamp
        lastPongReceived.put(session, Instant.now());

        // Subscribe to Redis Pub/Sub channel if this is the first connection to this room
        if (sessions.size() == 1) {
            eventSubscriber.subscribeToRoom(roomId);
        }

        Log.infof("Connection added to room %s: session %s (total in room: %d)",
                roomId, session.getId(), getConnectionCount(roomId));
    }

    /**
     * Removes a WebSocket connection from the registry.
     * <p>
     * Automatically cleans up empty room sets and heartbeat tracking data.
     * </p>
     *
     * @param session The WebSocket session to remove
     * @return The room ID the session was connected to, or null if not found
     */
    public String removeConnection(Session session) {
        // Get room ID from reverse mapping
        String roomId = sessionToRoom.remove(session);

        if (roomId != null) {
            // Remove session from room's connection set
            Set<Session> sessions = roomConnections.get(roomId);
            if (sessions != null) {
                sessions.remove(session);

                // Clean up empty room sets and unsubscribe from Redis
                if (sessions.isEmpty()) {
                    roomConnections.remove(roomId);
                    eventSubscriber.unsubscribeFromRoom(roomId);
                    Log.infof("Room %s has no more connections, removed from registry and unsubscribed from Redis", roomId);
                } else {
                    Log.infof("Connection removed from room %s: session %s (remaining: %d)",
                            roomId, session.getId(), sessions.size());
                }
            }
        }

        // Clean up heartbeat tracking
        lastPongReceived.remove(session);

        return roomId;
    }

    /**
     * Gets all active WebSocket sessions for a specific room.
     *
     * @param roomId The room ID
     * @return Set of active sessions (never null, may be empty)
     */
    public Set<Session> getConnectionsForRoom(String roomId) {
        return roomConnections.getOrDefault(roomId, ConcurrentHashMap.newKeySet());
    }

    /**
     * Gets the number of active connections in a room.
     *
     * @param roomId The room ID
     * @return The count of active connections
     */
    public int getConnectionCount(String roomId) {
        Set<Session> sessions = roomConnections.get(roomId);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Gets the room ID for a given WebSocket session.
     *
     * @param session The WebSocket session
     * @return The room ID, or null if session not registered
     */
    public String getRoomIdForSession(Session session) {
        return sessionToRoom.get(session);
    }

    /**
     * Checks if a session is registered in the connection registry.
     *
     * @param session The WebSocket session
     * @return true if session is registered, false otherwise
     */
    public boolean isSessionRegistered(Session session) {
        return sessionToRoom.containsKey(session);
    }

    /**
     * Updates the last pong received timestamp for heartbeat tracking.
     *
     * @param session The WebSocket session
     */
    public void updateLastPong(Session session) {
        if (sessionToRoom.containsKey(session)) {
            lastPongReceived.put(session, Instant.now());
            Log.debugf("Heartbeat updated for session %s", session.getId());
        }
    }

    /**
     * Gets the last pong received timestamp for a session.
     *
     * @param session The WebSocket session
     * @return The last pong timestamp, or null if not tracked
     */
    public Instant getLastPong(Session session) {
        return lastPongReceived.get(session);
    }

    /**
     * Gets all sessions that haven't sent a pong within the specified timeout.
     *
     * @param timeoutSeconds The timeout in seconds (typically 60)
     * @return Set of stale sessions
     */
    public Set<Session> getStaleSessions(int timeoutSeconds) {
        Set<Session> staleSessions = ConcurrentHashMap.newKeySet();
        Instant cutoffTime = Instant.now().minusSeconds(timeoutSeconds);

        lastPongReceived.forEach((session, lastPong) -> {
            if (lastPong.isBefore(cutoffTime)) {
                staleSessions.add(session);
            }
        });

        return staleSessions;
    }

    /**
     * Broadcasts a WebSocket message to all participants in a room.
     * <p>
     * This method serializes the message to JSON and sends it asynchronously
     * to all active connections in the specified room. Failed sends are logged
     * but do not prevent other participants from receiving the message.
     * </p>
     *
     * @param roomId The room ID to broadcast to
     * @param message The WebSocketMessage to send
     */
    public void broadcastToRoom(String roomId, WebSocketMessage message) {
        Set<Session> sessions = getConnectionsForRoom(roomId);

        if (sessions.isEmpty()) {
            Log.debugf("No active connections in room %s, skipping broadcast of %s",
                    roomId, message.getType());
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            int successCount = 0;
            int failureCount = 0;

            for (Session session : sessions) {
                try {
                    if (session.isOpen()) {
                        session.getAsyncRemote().sendText(json);
                        successCount++;
                    } else {
                        Log.warnf("Session %s is closed, skipping broadcast", session.getId());
                        failureCount++;
                    }
                } catch (Exception e) {
                    Log.errorf(e, "Failed to broadcast message to session %s", session.getId());
                    failureCount++;
                }
            }

            Log.infof("Broadcast %s to room %s: %d succeeded, %d failed",
                    message.getType(), roomId, successCount, failureCount);

        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to serialize WebSocket message: %s", message);
        }
    }

    /**
     * Sends a WebSocket message to a specific session (unicast).
     *
     * @param session The target session
     * @param message The WebSocketMessage to send
     */
    public void sendToSession(Session session, WebSocketMessage message) {
        if (!session.isOpen()) {
            Log.warnf("Cannot send message to closed session %s", session.getId());
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            session.getAsyncRemote().sendText(json);
            Log.debugf("Sent %s to session %s", message.getType(), session.getId());
        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to serialize WebSocket message: %s", message);
        } catch (Exception e) {
            Log.errorf(e, "Failed to send message to session %s", session.getId());
        }
    }

    /**
     * Gets the total number of active connections across all rooms.
     *
     * @return Total active connection count
     */
    public int getTotalConnectionCount() {
        return sessionToRoom.size();
    }

    /**
     * Gets the number of active rooms (rooms with at least one connection).
     *
     * @return Active room count
     */
    public int getActiveRoomCount() {
        return roomConnections.size();
    }

    /**
     * Gets all active WebSocket sessions across all rooms.
     * <p>
     * Used by the heartbeat mechanism to send ping frames to all connections.
     * </p>
     *
     * @return Set of all active sessions
     */
    public Set<Session> getAllSessions() {
        Set<Session> allSessions = ConcurrentHashMap.newKeySet();
        roomConnections.values().forEach(allSessions::addAll);
        return allSessions;
    }
}
