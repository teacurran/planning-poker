package com.scrumpoker.api.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Standard WebSocket message envelope following the Planning Poker WebSocket Protocol.
 * <p>
 * All WebSocket messages (both client→server and server→client) use this standardized
 * JSON envelope structure with type, requestId, and payload fields.
 * </p>
 * <p>
 * <strong>Message Type Naming Convention:</strong> {entity}.{action}.v{version}
 * <br>Examples: vote.cast.v1, room.join.v1, round.revealed.v1
 * </p>
 *
 * @see <a href="api/websocket-protocol.md">WebSocket Protocol Specification</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessage {

    /**
     * Versioned message type following pattern: entity.action.version
     * Examples: "vote.cast.v1", "room.participant_joined.v1"
     */
    @JsonProperty("type")
    private String type;

    /**
     * Unique request identifier (UUID v4) for request/response correlation.
     * Clients generate UUIDs for requests; server echoes same ID in responses/broadcasts.
     */
    @JsonProperty("requestId")
    private String requestId;

    /**
     * Message-specific data. Schema depends on message type.
     * May be empty object for some message types.
     */
    @JsonProperty("payload")
    private Map<String, Object> payload;

    /**
     * Default constructor for Jackson deserialization.
     */
    public WebSocketMessage() {
        this.payload = new HashMap<>();
    }

    /**
     * Creates a new WebSocket message with type, requestId, and payload.
     *
     * @param type The versioned message type (e.g., "vote.cast.v1")
     * @param requestId The unique request identifier (UUID v4)
     * @param payload The message-specific payload data
     */
    public WebSocketMessage(String type, String requestId, Map<String, Object> payload) {
        this.type = type;
        this.requestId = requestId;
        this.payload = payload != null ? payload : new HashMap<>();
    }

    /**
     * Creates a new WebSocket message with type and auto-generated requestId.
     *
     * @param type The versioned message type (e.g., "vote.cast.v1")
     * @param payload The message-specific payload data
     * @return New WebSocketMessage with auto-generated UUID requestId
     */
    public static WebSocketMessage create(String type, Map<String, Object> payload) {
        return new WebSocketMessage(type, UUID.randomUUID().toString(), payload);
    }

    /**
     * Creates a new WebSocket message with type and empty payload.
     *
     * @param type The versioned message type
     * @param requestId The unique request identifier
     * @return New WebSocketMessage with empty payload
     */
    public static WebSocketMessage createEmpty(String type, String requestId) {
        return new WebSocketMessage(type, requestId, new HashMap<>());
    }

    /**
     * Creates an error message (error.v1) with error details.
     *
     * @param requestId The request ID that triggered the error
     * @param code The error code (4000-4999 range)
     * @param error The error type (e.g., "UNAUTHORIZED", "INVALID_VOTE")
     * @param message Human-readable error message
     * @param timestamp The error timestamp (ISO-8601 format)
     * @return New error WebSocketMessage
     */
    public static WebSocketMessage createError(String requestId, int code, String error,
                                                String message, String timestamp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("code", code);
        payload.put("error", error);
        payload.put("message", message);
        payload.put("timestamp", timestamp);
        return new WebSocketMessage("error.v1", requestId, payload);
    }

    /**
     * Creates a participant_joined event message.
     *
     * @param participantId The participant ID
     * @param displayName The participant display name
     * @param role The participant role (HOST, VOTER, OBSERVER)
     * @param connectedAt The connection timestamp (ISO-8601)
     * @return New participant_joined WebSocketMessage
     */
    public static WebSocketMessage createParticipantJoined(String participantId, String displayName,
                                                            String role, String connectedAt) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("participantId", participantId);
        payload.put("displayName", displayName);
        payload.put("role", role);
        payload.put("connectedAt", connectedAt);
        return create("room.participant_joined.v1", payload);
    }

    /**
     * Creates a participant_left event message.
     *
     * @param participantId The participant ID
     * @param leftAt The disconnection timestamp (ISO-8601)
     * @param reason The reason for leaving (user_initiated, timeout, kicked)
     * @return New participant_left WebSocketMessage
     */
    public static WebSocketMessage createParticipantLeft(String participantId, String leftAt,
                                                          String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("participantId", participantId);
        payload.put("leftAt", leftAt);
        payload.put("reason", reason);
        return create("room.participant_left.v1", payload);
    }

    // Getters and Setters

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }

    /**
     * Adds a field to the payload.
     *
     * @param key The field name
     * @param value The field value
     * @return This message (for method chaining)
     */
    public WebSocketMessage addPayloadField(String key, Object value) {
        this.payload.put(key, value);
        return this;
    }

    /**
     * Gets a field from the payload.
     *
     * @param key The field name
     * @return The field value, or null if not present
     */
    public Object getPayloadField(String key) {
        return this.payload.get(key);
    }

    @Override
    public String toString() {
        return "WebSocketMessage{" +
                "type='" + type + '\'' +
                ", requestId='" + requestId + '\'' +
                ", payload=" + payload +
                '}';
    }
}
