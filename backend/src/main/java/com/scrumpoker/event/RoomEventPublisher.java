package com.scrumpoker.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.pubsub.ReactivePubSubCommands;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

/**
 * Service for publishing WebSocket events to Redis Pub/Sub channels.
 * <p>
 * This publisher enables event broadcasting across all application nodes
 * in a horizontally scaled deployment. Events are serialized as JSON and
 * published to room-specific Redis channels.
 * </p>
 * <p>
 * <strong>Channel Naming Convention:</strong> room:{roomId}
 * <br>Example: room:abc123
 * </p>
 * <p>
 * The {@link RoomEventSubscriber} on each application node subscribes to
 * these channels and forwards events to locally connected WebSocket clients.
 * </p>
 *
 * @see RoomEventSubscriber
 * @see RoomEvent
 */
@ApplicationScoped
public class RoomEventPublisher {

    /**
     * Reactive Redis data source for pub/sub operations.
     */
    @Inject
    private ReactiveRedisDataSource redisDataSource;

    /**
     * Jackson ObjectMapper for JSON serialization.
     */
    @Inject
    private ObjectMapper objectMapper;

    /**
     * Redis Pub/Sub commands interface.
     */
    private ReactivePubSubCommands<String> pubsub;

    /**
     * Initializes the Redis Pub/Sub publisher.
     * <p>
     * This method is called automatically by CDI after injection.
     * </p>
     */
    @jakarta.annotation.PostConstruct
    void initialize() {
        this.pubsub = redisDataSource.pubsub(String.class);
        Log.info("RoomEventPublisher initialized with Redis Pub/Sub");
    }

    /**
     * Publishes an event to the Redis Pub/Sub channel for the specified room.
     * <p>
     * This method is non-blocking and returns a Uni that completes when the
     * event has been published to Redis. The event will be broadcast to all
     * application nodes subscribed to the room's channel.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param type The versioned event type (e.g., "vote.recorded.v1")
     * @param payload The event-specific payload data
     * @return Uni<Void> that completes when publish succeeds
     */
    public Uni<Void> publishEvent(final String roomId, final String type,
                                   final Map<String, Object> payload) {
        return publishEvent(roomId, type, null, payload);
    }

    /**
     * Publishes an event to the Redis Pub/Sub channel for the specified room.
     * <p>
     * This overload allows specifying a requestId for request/response
     * correlation when the event is triggered by a client WebSocket
     * message.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param type The versioned event type (e.g., "vote.recorded.v1")
     * @param requestId The client's request ID to echo back (may be null)
     * @param payload The event-specific payload data
     * @return Uni<Void> that completes when publish succeeds
     */
    public Uni<Void> publishEvent(final String roomId, final String type,
                                   final String requestId,
                                   final Map<String, Object> payload) {
        String channel = buildChannelName(roomId);
        RoomEvent event = requestId != null
                ? new RoomEvent(type, requestId, payload)
                : RoomEvent.create(type, payload);

        return Uni.createFrom().item(event)
                .onItem().transform(this::serializeEvent)
                .onItem().transformToUni(json ->
                        publishToChannel(channel, json, event))
                .onFailure().invoke(failure ->
                        Log.errorf(failure,
                                "Failed to publish event %s to room %s",
                                type, roomId)
                );
    }

    /**
     * Serializes a RoomEvent to JSON.
     *
     * @param event The event to serialize
     * @return JSON string representation
     * @throws RuntimeException if serialization fails
     */
    private String serializeEvent(final RoomEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to serialize RoomEvent: %s", event);
            throw new RuntimeException("Event serialization failed", e);
        }
    }

    /**
     * Publishes JSON message to Redis Pub/Sub channel.
     *
     * @param channel The Redis channel name
     * @param json The JSON message to publish
     * @param event The original event (for logging)
     * @return Uni<Void> that completes when publish succeeds
     */
    private Uni<Void> publishToChannel(final String channel,
                                        final String json,
                                        final RoomEvent event) {
        return pubsub.publish(channel, json)
                .onItem().invoke(subscriberCount ->
                        Log.infof("Published event %s to channel %s "
                                + "(reached %d subscribers)",
                                event.getType(), channel, subscriberCount)
                )
                .onItem().ignore().andContinueWithNull();
    }

    /**
     * Builds the Redis channel name for a room.
     *
     * @param roomId The room ID
     * @return The channel name (e.g., "room:abc123")
     */
    private String buildChannelName(final String roomId) {
        return "room:" + roomId;
    }
}
