package com.scrumpoker.api.websocket.handler;

import com.scrumpoker.api.websocket.WebSocketMessage;
import io.smallrye.mutiny.Uni;
import jakarta.websocket.Session;

/**
 * Common interface for WebSocket message handlers.
 * <p>
 * Each handler processes a specific message type (e.g., vote.cast.v1, round.reveal.v1)
 * and implements the business logic for that message.
 * </p>
 * <p>
 * Handlers are discovered via CDI and registered with the MessageRouter at startup.
 * All handlers must be @ApplicationScoped beans.
 * </p>
 */
public interface MessageHandler {

    /**
     * Returns the message type this handler processes.
     *
     * @return The message type (e.g., "vote.cast.v1", "round.reveal.v1")
     */
    String getMessageType();

    /**
     * Handles a WebSocket message asynchronously.
     * <p>
     * This method performs:
     * <ul>
     *   <li>Payload validation</li>
     *   <li>Authorization checks (role-based permissions)</li>
     *   <li>Business logic execution (calling domain services)</li>
     *   <li>Error handling (converting exceptions to error messages)</li>
     * </ul>
     * </p>
     *
     * @param session The WebSocket session
     * @param message The parsed WebSocket message
     * @param userId The authenticated user ID (from session properties)
     * @param roomId The room ID (from session properties)
     * @return Uni<Void> that completes when message is processed
     */
    Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId);
}
