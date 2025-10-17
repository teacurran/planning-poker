package com.scrumpoker.event;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Event message envelope for Redis Pub/Sub room broadcasts.
 * <p>
 * This class represents events published to Redis channels (room:{roomId})
 * that are distributed across all application nodes and forwarded to
 * WebSocket clients within the target room.
 * </p>
 * <p>
 * <strong>Channel Naming Convention:</strong> room:{roomId}
 * <br>Example: room:abc123
 * </p>
 * <p>
 * The structure mirrors {@link com.scrumpoker.api.websocket.WebSocketMessage}
 * to enable seamless deserialization and forwarding to WebSocket clients.
 * </p>
 *
 * @see com.scrumpoker.api.websocket.WebSocketMessage
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoomEvent {

    /**
     * Versioned message type following pattern: entity.action.version.
     * Examples: "vote.recorded.v1", "round.revealed.v1"
     */
    @JsonProperty("type")
    private String type;

    /**
     * Unique request identifier (UUID v4) for request/response correlation.
     * Echoed from original client request when applicable.
     */
    @JsonProperty("requestId")
    private String requestId;

    /**
     * Event-specific data. Schema depends on event type.
     */
    @JsonProperty("payload")
    private Map<String, Object> payload;

    /**
     * Default constructor for Jackson deserialization.
     */
    public RoomEvent() {
        this.payload = new HashMap<>();
    }

    /**
     * Creates a new room event with type, requestId, and payload.
     *
     * @param eventType The versioned event type (e.g., "vote.recorded.v1")
     * @param eventRequestId The unique request identifier (UUID v4)
     * @param eventPayload The event-specific payload data
     */
    public RoomEvent(final String eventType, final String eventRequestId,
                      final Map<String, Object> eventPayload) {
        this.type = eventType;
        this.requestId = eventRequestId;
        this.payload = eventPayload != null ? eventPayload : new HashMap<>();
    }

    /**
     * Creates a new room event with type and auto-generated requestId.
     *
     * @param type The versioned event type
     * @param payload The event-specific payload data
     * @return New RoomEvent with auto-generated UUID requestId
     */
    public static RoomEvent create(final String type,
                                    final Map<String, Object> payload) {
        return new RoomEvent(type, UUID.randomUUID().toString(), payload);
    }

    /**
     * Creates a new room event with type and empty payload.
     *
     * @param type The versioned event type
     * @param requestId The unique request identifier
     * @return New RoomEvent with empty payload
     */
    public static RoomEvent createEmpty(final String type,
                                         final String requestId) {
        return new RoomEvent(type, requestId, new HashMap<>());
    }

    // Getters and Setters

    /**
     * Gets the event type.
     *
     * @return The versioned event type
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the event type.
     *
     * @param eventType The versioned event type
     */
    public void setType(final String eventType) {
        this.type = eventType;
    }

    /**
     * Gets the request ID.
     *
     * @return The unique request identifier
     */
    public String getRequestId() {
        return requestId;
    }

    /**
     * Sets the request ID.
     *
     * @param eventRequestId The unique request identifier
     */
    public void setRequestId(final String eventRequestId) {
        this.requestId = eventRequestId;
    }

    /**
     * Gets the payload.
     *
     * @return The event payload map
     */
    public Map<String, Object> getPayload() {
        return payload;
    }

    /**
     * Sets the payload.
     *
     * @param eventPayload The event payload map
     */
    public void setPayload(final Map<String, Object> eventPayload) {
        this.payload = eventPayload;
    }

    /**
     * Adds a field to the payload.
     *
     * @param key The field name
     * @param value The field value
     * @return This event (for method chaining)
     */
    public RoomEvent addPayloadField(final String key, final Object value) {
        this.payload.put(key, value);
        return this;
    }

    /**
     * Gets a field from the payload.
     *
     * @param key The field name
     * @return The field value, or null if not present
     */
    public Object getPayloadField(final String key) {
        return this.payload.get(key);
    }

    /**
     * Returns string representation of this event.
     *
     * @return String representation
     */
    @Override
    public String toString() {
        return "RoomEvent{"
                + "type='" + type + '\''
                + ", requestId='" + requestId + '\''
                + ", payload=" + payload
                + '}';
    }
}
