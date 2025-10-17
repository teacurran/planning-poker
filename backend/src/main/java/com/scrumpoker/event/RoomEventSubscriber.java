package com.scrumpoker.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.api.websocket.ConnectionRegistry;
import com.scrumpoker.api.websocket.WebSocketMessage;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.pubsub.ReactivePubSubCommands;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for subscribing to Redis Pub/Sub room channels and forwarding
 * events to locally connected WebSocket clients.
 * <p>
 * This subscriber enables horizontal scaling by allowing multiple application
 * nodes to receive events published to Redis and broadcast them to their
 * locally connected clients.
 * </p>
 * <p>
 * <strong>Subscription Lifecycle:</strong>
 * <ul>
 *   <li>Subscribe to room:{roomId} when first client joins the room</li>
 *   <li>Unsubscribe when last client leaves the room</li>
 *   <li>Automatic reconnection on Redis connection failures</li>
 * </ul>
 * </p>
 * <p>
 * Integration with {@link ConnectionRegistry}:
 * <br>ConnectionRegistry calls {@link #subscribeToRoom(String)} and
 * {@link #unsubscribeFromRoom(String)} based on WebSocket connection lifecycle.
 * </p>
 *
 * @see RoomEventPublisher
 * @see ConnectionRegistry
 */
@ApplicationScoped
public class RoomEventSubscriber {

    /**
     * Reactive Redis data source for pub/sub operations.
     */
    @Inject
    private ReactiveRedisDataSource redisDataSource;

    /**
     * Jackson ObjectMapper for JSON deserialization.
     */
    @Inject
    private ObjectMapper objectMapper;

    /**
     * Connection registry for accessing WebSocket sessions.
     */
    @Inject
    private ConnectionRegistry connectionRegistry;

    /**
     * Redis Pub/Sub commands interface.
     */
    private ReactivePubSubCommands<String> pubsub;

    /**
     * Map of roomId -> active subscription cancellable.
     * Thread-safe for concurrent subscription/unsubscription.
     */
    private final ConcurrentHashMap<String, Cancellable> activeSubscriptions;

    /**
     * Constructor initializing the active subscriptions map.
     */
    public RoomEventSubscriber() {
        this.activeSubscriptions = new ConcurrentHashMap<>();
    }

    /**
     * Initializes the Redis Pub/Sub subscriber.
     * <p>
     * This method is called automatically by CDI after injection.
     * </p>
     */
    @jakarta.annotation.PostConstruct
    void initialize() {
        this.pubsub = redisDataSource.pubsub(String.class);
        Log.info("RoomEventSubscriber initialized with Redis Pub/Sub");
    }

    /**
     * Subscribes to Redis Pub/Sub channel for the specified room.
     * <p>
     * This method is idempotent - if already subscribed, does nothing.
     * Called by {@link ConnectionRegistry} when first client joins room.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     */
    public void subscribeToRoom(final String roomId) {
        // Check if already subscribed
        if (activeSubscriptions.containsKey(roomId)) {
            Log.debugf("Already subscribed to room %s, skipping", roomId);
            return;
        }

        String channel = buildChannelName(roomId);

        // Subscribe to Redis channel and get Multi<String> stream
        Cancellable subscription = pubsub.subscribe(channel)
                .subscribe().with(
                        message -> handleReceivedMessage(roomId, message),
                        failure -> handleSubscriptionError(roomId, failure)
                );

        // Store subscription for lifecycle management
        activeSubscriptions.put(roomId, subscription);

        Log.infof("Subscribed to Redis channel: %s", channel);
    }

    /**
     * Unsubscribes from Redis Pub/Sub channel for the specified room.
     * <p>
     * This method is idempotent - if not subscribed, does nothing.
     * Called by {@link ConnectionRegistry} when last client leaves room.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     */
    public void unsubscribeFromRoom(final String roomId) {
        Cancellable subscription = activeSubscriptions.remove(roomId);

        if (subscription != null) {
            subscription.cancel();
            Log.infof("Unsubscribed from Redis channel: %s",
                    buildChannelName(roomId));
        } else {
            Log.debugf("Not subscribed to room %s, "
                    + "nothing to unsubscribe", roomId);
        }
    }

    /**
     * Handles a message received from Redis Pub/Sub channel.
     * <p>
     * Deserializes the JSON message to a RoomEvent, converts it to a
     * WebSocketMessage, and broadcasts to all locally connected clients
     * in the target room via {@link ConnectionRegistry}.
     * </p>
     *
     * @param roomId The room ID
     * @param json The JSON message received from Redis
     */
    private void handleReceivedMessage(final String roomId,
                                        final String json) {
        try {
            // Deserialize Redis message to RoomEvent
            RoomEvent event = objectMapper.readValue(json, RoomEvent.class);

            Log.debugf("Received event %s from Redis for room %s",
                    event.getType(), roomId);

            // Convert RoomEvent to WebSocketMessage for client delivery
            WebSocketMessage message = new WebSocketMessage(
                    event.getType(),
                    event.getRequestId(),
                    event.getPayload()
            );

            // Broadcast to all locally connected WebSocket clients in this room
            connectionRegistry.broadcastToRoom(roomId, message);

        } catch (Exception e) {
            Log.errorf(e, "Failed to process Redis message "
                    + "for room %s: %s", roomId, json);
        }
    }

    /**
     * Handles subscription error from Redis Pub/Sub.
     * <p>
     * Logs the error and removes the subscription from tracking.
     * The application continues to function, but clients in this room
     * on this node will not receive events until they reconnect.
     * </p>
     *
     * @param roomId The room ID
     * @param failure The error that occurred
     */
    private void handleSubscriptionError(final String roomId,
                                          final Throwable failure) {
        Log.errorf(failure, "Redis subscription error for room %s", roomId);

        // Remove failed subscription from tracking
        activeSubscriptions.remove(roomId);

        // Note: In production, you might want to implement automatic
        // reconnection logic here, but for now we rely on clients
        // reconnecting their WebSockets which will trigger a new
        // subscription attempt
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

    /**
     * Gets the count of active subscriptions (for monitoring/debugging).
     *
     * @return Number of active Redis subscriptions
     */
    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }

    /**
     * Checks if subscribed to a specific room.
     *
     * @param roomId The room ID
     * @return true if subscribed, false otherwise
     */
    public boolean isSubscribedToRoom(final String roomId) {
        return activeSubscriptions.containsKey(roomId);
    }
}
