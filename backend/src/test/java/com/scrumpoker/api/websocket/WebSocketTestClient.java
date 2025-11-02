package com.scrumpoker.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.websocket.*;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.jakarta.client.JakartaWebSocketClientContainerProvider;

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
 * Uses Jetty's standalone WebSocket client to avoid Quarkus server container's
 * RequestScoped context issues (SecurityIdentityAssociation bean access).
 * </p>
 */
@ClientEndpoint
public class WebSocketTestClient {

    private static final WebSocketContainer clientContainer;

    static {
        try {
            // Create a standalone Jetty HttpClient for the WebSocket container
            // This bypasses ContainerProvider.getWebSocketContainer() which returns
            // Quarkus's server container with @RequestScoped bean dependencies
            HttpClient httpClient = new HttpClient();
            httpClient.start();
            clientContainer = JakartaWebSocketClientContainerProvider.getContainer(httpClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Jetty WebSocket client container", e);
        }
    }

    private Session session;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlockingQueue<WebSocketMessage> receivedMessages = new LinkedBlockingQueue<>();

    /**
     * Connects to the WebSocket endpoint using Jetty's standalone client.
     * <p>
     * This bypasses Quarkus's server WebSocket container entirely, avoiding
     * the ContextNotActiveException that occurs when the server container
     * tries to access @RequestScoped beans like SecurityIdentityAssociation.
     * </p>
     * <p>
     * After the connection is established, this method waits briefly (100ms) to
     * ensure the server's @OnOpen async validation completes before the test
     * proceeds to send messages. This prevents race conditions where room.join.v1
     * is sent before the server has stored the userId and roomId in session properties.
     * </p>
     *
     * @param uri The WebSocket URI (e.g., "ws://localhost:8081/ws/room/abc123?token=xxx")
     * @throws Exception if connection fails
     */
    public void connect(String uri) throws Exception {
        session = clientContainer.connectToServer(this, URI.create(uri));
        // Wait briefly for server's @OnOpen async validation to complete
        // This prevents race condition where roomId/userId are not yet set in session
        Thread.sleep(100);
    }

    /**
     * Closes the WebSocket connection.
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
