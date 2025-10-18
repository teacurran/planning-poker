package com.scrumpoker.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import jakarta.websocket.*;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * WebSocket test client for integration tests.
 * <p>
 * Provides a simple interface for connecting to WebSocket endpoints,
 * sending messages, and receiving messages with timeout support.
 * </p>
 * <p>
 * Uses standard Jakarta WebSocket API. Security is disabled in test configuration
 * to avoid RequestScoped context issues during client connection.
 * </p>
 */
@ClientEndpoint
public class WebSocketTestClient {

    private Session session;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<WebSocketMessage> receivedMessages = new LinkedBlockingQueue<>();

    /**
     * Connects to the WebSocket endpoint.
     * <p>
     * Activates the RequestScoped context before connecting to avoid
     * ContextNotActiveException when the WebSocket container tries to
     * access security-related beans.
     * </p>
     * <p>
     * IMPORTANT: The request context is kept active after connection and must
     * be explicitly terminated by calling close().
     * </p>
     *
     * @param uri The WebSocket URI (e.g., "ws://localhost:8081/ws/room/abc123?token=xxx")
     * @throws Exception if connection fails
     */
    public void connect(String uri) throws Exception {
        ManagedContext requestContext = Arc.container().requestContext();

        // Activate request context for the connection and keep it active
        if (!requestContext.isActive()) {
            requestContext.activate();
        }

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        session = container.connectToServer(this, URI.create(uri));
    }

    /**
     * Closes the WebSocket connection.
     * <p>
     * Note: We intentionally do NOT terminate the request context here because
     * it may be shared across multiple WebSocket clients in the same test.
     * The context will be automatically cleaned up when the test completes.
     * </p>
     */
    public void close() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (Exception e) {
                // Ignore close errors in tests
            }
        }
    }

    /**
     * Sends a WebSocket message.
     *
     * @param type The message type (e.g., "vote.cast.v1")
     * @param payload The message payload
     * @return The generated request ID
     * @throws Exception if sending fails
     */
    public String send(String type, Map<String, Object> payload) throws Exception {
        String requestId = UUID.randomUUID().toString();
        WebSocketMessage message = new WebSocketMessage(type, requestId, payload);
        String json = objectMapper.writeValueAsString(message);
        session.getBasicRemote().sendText(json);
        return requestId;
    }

    /**
     * Sends a WebSocket message with a specific request ID.
     *
     * @param type The message type
     * @param requestId The request ID
     * @param payload The message payload
     * @throws Exception if sending fails
     */
    public void send(String type, String requestId, Map<String, Object> payload) throws Exception {
        WebSocketMessage message = new WebSocketMessage(type, requestId, payload);
        String json = objectMapper.writeValueAsString(message);
        session.getBasicRemote().sendText(json);
    }

    /**
     * Waits for a message of a specific type.
     *
     * @param messageType The expected message type
     * @param timeout The timeout duration
     * @return The received message, or null if timeout
     * @throws InterruptedException if interrupted while waiting
     */
    public WebSocketMessage awaitMessage(String messageType, Duration timeout) throws InterruptedException {
        long timeoutMs = timeout.toMillis();
        long deadline = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < deadline) {
            long remaining = deadline - System.currentTimeMillis();
            WebSocketMessage message = receivedMessages.poll(remaining, TimeUnit.MILLISECONDS);

            if (message == null) {
                return null; // Timeout
            }

            if (messageType.equals(message.getType())) {
                return message;
            }

            // Wrong type, put it back and continue waiting
            receivedMessages.offer(message);
            Thread.sleep(50); // Brief pause before re-checking
        }

        return null; // Timeout
    }

    /**
     * Gets the next message from the queue without waiting.
     *
     * @return The next message, or null if queue is empty
     */
    public WebSocketMessage pollMessage() {
        return receivedMessages.poll();
    }

    /**
     * Clears all received messages from the queue.
     */
    public void clearMessages() {
        receivedMessages.clear();
    }

    /**
     * Called when a text message is received.
     */
    @OnMessage
    public void onMessage(String messageText) {
        try {
            WebSocketMessage message = objectMapper.readValue(messageText, WebSocketMessage.class);
            receivedMessages.offer(message);
        } catch (Exception e) {
            System.err.println("Failed to parse WebSocket message: " + e.getMessage());
        }
    }

    /**
     * Called when the connection is opened.
     */
    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
    }

    /**
     * Called when the connection is closed.
     */
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        // Connection closed
    }

    /**
     * Called when an error occurs.
     */
    @OnError
    public void onError(Session session, Throwable throwable) {
        System.err.println("WebSocket error: " + throwable.getMessage());
    }

    /**
     * Checks if the connection is open.
     */
    public boolean isOpen() {
        return session != null && session.isOpen();
    }

    /**
     * Helper method to create a payload map.
     */
    public static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
