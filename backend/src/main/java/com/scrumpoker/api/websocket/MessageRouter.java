package com.scrumpoker.api.websocket;

import com.scrumpoker.api.websocket.handler.MessageHandler;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.util.HashMap;
import java.util.Map;

/**
 * Routes incoming WebSocket messages to appropriate handlers based on message type.
 * <p>
 * This router uses CDI to automatically discover all MessageHandler beans at startup
 * and creates a type-to-handler mapping for efficient message dispatching.
 * </p>
 * <p>
 * The router is integrated into RoomWebSocketHandler.onMessage() and handles all
 * message types except room.join.v1 and room.leave.v1 (which are handled directly
 * by RoomWebSocketHandler).
 * </p>
 */
@ApplicationScoped
public class MessageRouter {

    @Inject
    Instance<MessageHandler> handlers;

    private Map<String, MessageHandler> handlerMap;

    /**
     * Initializes the router by discovering all MessageHandler beans
     * and building the message type to handler mapping.
     */
    @PostConstruct
    void init() {
        handlerMap = new HashMap<>();

        for (MessageHandler handler : handlers) {
            String messageType = handler.getMessageType();
            handlerMap.put(messageType, handler);
            Log.infof("Registered message handler: %s -> %s",
                    messageType, handler.getClass().getSimpleName());
        }

        Log.infof("MessageRouter initialized with %d handlers", handlerMap.size());
    }

    /**
     * Routes a WebSocket message to the appropriate handler based on message type.
     * <p>
     * If no handler is registered for the message type, the message is silently ignored
     * (this allows for graceful handling of unknown message types).
     * </p>
     *
     * @param session The WebSocket session
     * @param message The parsed WebSocket message
     * @param userId The authenticated user ID (from session properties)
     * @param roomId The room ID (from session properties)
     * @return Uni<Void> that completes when message is processed
     */
    public Uni<Void> route(Session session, WebSocketMessage message, String userId, String roomId) {
        String messageType = message.getType();
        MessageHandler handler = handlerMap.get(messageType);

        if (handler == null) {
            Log.debugf("No handler registered for message type: %s (ignoring)", messageType);
            return Uni.createFrom().voidItem();
        }

        Log.debugf("Routing message %s to handler %s",
                messageType, handler.getClass().getSimpleName());

        return handler.handle(session, message, userId, roomId);
    }

    /**
     * Checks if a handler is registered for a given message type.
     *
     * @param messageType The message type
     * @return true if handler exists, false otherwise
     */
    public boolean hasHandler(String messageType) {
        return handlerMap.containsKey(messageType);
    }

    /**
     * Gets the number of registered handlers.
     *
     * @return Handler count
     */
    public int getHandlerCount() {
        return handlerMap.size();
    }
}
