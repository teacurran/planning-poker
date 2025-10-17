# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T2",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Create `RoomEventPublisher` service publishing WebSocket events to Redis Pub/Sub channel `room:{roomId}`. Implement `RoomEventSubscriber` subscribing to channels and forwarding messages to locally connected WebSocket clients. Use Quarkus reactive Redis client. Publisher method: `publishEvent(roomId, messageType, payload)` serializes message to JSON and publishes. Subscriber: on message received, look up connections in ConnectionRegistry for that room, send message to each WebSocket session. Handle subscription lifecycle (subscribe when first client joins room, unsubscribe when last client leaves).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Redis Pub/Sub pattern from architecture blueprint, WebSocket message broadcasting requirements",
  "input_files": [
    ".codemachine/artifacts/architecture/04_Behavior_and_Communication.md",
    "backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/event/RoomEventPublisher.java",
    "backend/src/main/java/com/scrumpoker/event/RoomEventSubscriber.java",
    "backend/src/main/java/com/scrumpoker/event/RoomEvent.java"
  ],
  "deliverables": "RoomEventPublisher with `publishEvent(roomId, type, payload)` method, Redis Pub/Sub channel naming: `room:{roomId}`, RoomEventSubscriber listening to subscribed channels, Event routing to WebSocket clients via ConnectionRegistry, JSON serialization/deserialization for event payloads, Subscription management (subscribe/unsubscribe based on room activity)",
  "acceptance_criteria": "Publishing event to Redis channel succeeds, Subscriber receives event from Redis, Event forwarded to all connected WebSocket clients in target room, Events not sent to clients in other rooms, Multiple application nodes can publish/subscribe (test with 2 backend instances), Subscription cleaned up when no clients in room",
  "dependencies": ["I4.T1"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: asynchronous-websocket-pattern (from 04_Behavior_and_Communication.md)

```markdown
##### Asynchronous WebSocket (Event-Driven)

**Use Cases:**
- Real-time vote casting and vote state updates
- Room state synchronization (participant joins/leaves, host controls)
- Card reveal events with animated timing coordination
- Presence updates (typing indicators, ready states)
- Chat messages and emoji reactions

**Pattern Characteristics:**
- Persistent connection maintained for session duration
- Events broadcast via Redis Pub/Sub to all application nodes
- Client-side event handlers update local state optimistically, reconcile on server confirmation
- Heartbeat/ping-pong protocol for connection liveness detection
- Automatic reconnection with exponential backoff on connection loss

**Message Flow:**
1. Client sends WebSocket message: `{"type": "vote.cast.v1", "requestId": "uuid", "payload": {"cardValue": "5"}}`
2. Server validates, persists vote to PostgreSQL
3. Server publishes event to Redis channel: `room:{roomId}`
4. All application nodes subscribed to channel receive event
5. Each node broadcasts to locally connected clients in that room
6. Clients receive: `{"type": "vote.recorded.v1", "requestId": "uuid", "payload": {"participantId": "...", "votedAt": "..."}}`

**WebSocket Message Types:**
- `room.join.v1` - Participant joins room
- `room.leave.v1` - Participant exits room
- `vote.cast.v1` - Participant submits vote
- `vote.recorded.v1` - Server confirms vote persisted (broadcast to room)
- `round.reveal.v1` - Host triggers card reveal
- `round.revealed.v1` - Server broadcasts reveal with statistics
- `round.reset.v1` - Host resets round for re-voting
- `chat.message.v1` - Participant sends chat message
- `presence.update.v1` - Participant status change (ready, away)
- `error.v1` - Server-side validation or authorization error
```

### Context: key-interaction-flow-vote-round (from 04_Behavior_and_Communication.md)

```markdown
#### Key Interaction Flow: Vote Casting and Round Reveal

##### Description

This sequence diagram illustrates the critical real-time workflow for a Scrum Poker estimation round, from initial vote casting through final reveal and consensus calculation. The flow demonstrates WebSocket message handling, Redis Pub/Sub event distribution across stateless application nodes, and optimistic UI updates with server reconciliation.

**Scenario:**
1. Two participants (Alice and Bob) connected to different application nodes due to load balancer sticky session routing
2. Alice casts vote "5", Bob casts vote "8"
3. Host triggers reveal after all votes submitted
4. System calculates statistics (average: 6.5, median: 6.5, no consensus due to variance)
5. All participants receive synchronized reveal event with results

[Key portions from PlantUML diagram showing Redis Pub/Sub flow:]

VS_A -> Redis : PUBLISH room:abc123 {"type":"vote.recorded.v1", "payload":{"participantId":"alice", "votedAt":"..."}}

Redis -> WS_A : Subscriber receives: vote.recorded.v1 (alice)
Redis -> WS_B : Subscriber receives: vote.recorded.v1 (alice)
Redis -> WS_C : Subscriber receives: vote.recorded.v1 (alice)

[Each WebSocket handler then broadcasts to locally connected clients]
WS_A -> SPA_C : Broadcast to Charlie
WS_B -> SPA_A : Broadcast to Alice (confirmation)
WS_B -> SPA_B : Broadcast to Bob
```

### Context: scalability-and-performance / horizontal-scaling (from 05_Operational_Architecture.md)

```markdown
##### Horizontal Scaling

**Stateless Application Design:**
- **Session State:** Stored in Redis, not in JVM memory, enabling any node to serve any request
- **WebSocket Affinity:** Load balancer sticky sessions based on `room_id` hash for optimal Redis Pub/Sub efficiency, but not required for correctness
- **Database Connection Pooling:** HikariCP with max pool size = (core_count * 2) + effective_spindle_count, distributed across replicas

**Redis Scaling:**
- **Cluster Mode:** 3-node Redis cluster for horizontal scalability and high availability
- **Pub/Sub Sharding:** Channels sharded by `room_id` hash for distributed subscription load
- **Eviction Policy:** `allkeys-lru` for session cache, `noeviction` for critical room state (manual TTL management)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
    *   **Summary:** Thread-safe registry managing active WebSocket sessions per room. Uses ConcurrentHashMap to store roomId -> Set<Session> mappings. Provides critical methods: `broadcastToRoom(roomId, message)` and `getConnectionsForRoom(roomId)`.
    *   **Recommendation:** You MUST inject `ConnectionRegistry` into your `RoomEventSubscriber` to access locally connected WebSocket sessions. Use `connectionRegistry.broadcastToRoom(roomId, message)` to forward Redis Pub/Sub events to local clients.
    *   **Note:** The ConnectionRegistry already handles JSON serialization (via ObjectMapper) and async sending via `session.getAsyncRemote().sendText()`. Your subscriber should pass deserialized `WebSocketMessage` objects directly to `broadcastToRoom()`.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Summary:** WebSocket endpoint handler implementing connection lifecycle (onOpen, onClose, onMessage, onError). Validates JWT tokens, manages heartbeat protocol, and delegates to ConnectionRegistry for session management.
    *   **Recommendation:** This class is already complete from I4.T1. You will NOT modify this file. However, you SHOULD understand that `onOpen` calls `connectionRegistry.addConnection(roomId, session)` and `onClose` calls `connectionRegistry.removeConnection(session)`. Your subscription lifecycle logic MUST hook into these events.
    *   **Integration Point:** The RoomWebSocketHandler will eventually call your `RoomEventPublisher.publishEvent()` from message handlers (in I4.T4), but for I4.T2 you are only implementing the infrastructure.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/WebSocketMessage.java`
    *   **Summary:** Message envelope class with fields: `type`, `requestId`, `payload` (Map<String, Object>). Includes static factory methods like `createParticipantJoined()`, `createError()`.
    *   **Recommendation:** Your `RoomEvent` class SHOULD follow a similar structure. The event payload published to Redis should be serializable as JSON and contain enough information to reconstruct a `WebSocketMessage` on the receiving end.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** Application configuration including Redis connection settings. Redis configured at line 41: `quarkus.redis.hosts=${REDIS_URL:redis://localhost:6379}` with standalone client type (line 42).
    *   **Recommendation:** You MUST use the Quarkus reactive Redis client (`io.quarkus.redis.datasource.ReactiveRedisDataSource`) which is already configured. Use reactive patterns with Mutiny (Uni/Multi) for non-blocking pub/sub operations.
    *   **Configuration Note:** The Redis client type is `standalone` (not cluster) for development. Your implementation should work with both, but the architecture specifies cluster mode for production (3 nodes).

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven dependencies including `quarkus-redis-client` (line 76-78) which provides reactive Redis support.
    *   **Recommendation:** All required dependencies are already present. You do NOT need to modify pom.xml.

### Implementation Tips & Notes

*   **Tip:** The Quarkus reactive Redis client provides `pubsub()` methods for publishing and subscribing. Use `redisDataSource.pubsub(String.class).subscribe(channelPattern)` to get a Multi<String> stream of messages. Use `redisDataSource.pubsub(String.class).publish(channel, message)` to send events.

*   **Note:** Redis Pub/Sub channels are formatted as `room:{roomId}` where `{roomId}` is the 6-character nanoid. Your subscriber MUST use pattern matching if you want to subscribe to all rooms dynamically, OR manage subscriptions per-room (recommended approach based on the task description).

*   **Critical Design Decision:** The task specifies "subscribe when first client joins room, unsubscribe when last client leaves". This means:
    1. You MUST track active room subscriptions (e.g., `ConcurrentHashMap<String, Subscription>`)
    2. When `ConnectionRegistry.addConnection()` is the FIRST connection for a room, call `subscribe(room:roomId)`
    3. When `ConnectionRegistry.removeConnection()` empties a room, call subscription.cancel() and clean up
    4. You will need to integrate with ConnectionRegistry's lifecycle, possibly by making your subscriber `@ApplicationScoped` and having ConnectionRegistry notify it of room join/leave events.

*   **Alternative Design Pattern:** Since ConnectionRegistry is already managing room lifecycle, consider having your `RoomEventSubscriber` provide a `subscribeToRoom(roomId)` and `unsubscribeFromRoom(roomId)` method that ConnectionRegistry calls. This is cleaner than trying to observe ConnectionRegistry state changes.

*   **JSON Serialization:** Use the injected `ObjectMapper` instance (same as ConnectionRegistry uses) to serialize/deserialize your `RoomEvent` objects. Ensure consistency with WebSocketMessage JSON format.

*   **Testing Strategy:** The acceptance criteria requires testing with 2 backend instances. In your integration test, you can simulate this by:
    1. Publishing an event using RoomEventPublisher
    2. Verifying your RoomEventSubscriber receives it
    3. Verifying ConnectionRegistry.broadcastToRoom() was called with correct roomId and message
    4. You do NOT need to test actual multiple JVM instances in I4.T2 - that will be covered in I4.T7's integration tests

*   **Error Handling:** If Redis Pub/Sub fails (e.g., Redis connection lost), your subscriber should log the error but NOT crash the application. WebSocket clients may miss some events, but the system should continue to function. Consider implementing reconnection logic for the Redis subscription.

*   **Thread Safety:** Quarkus reactive Redis operations are non-blocking and thread-safe. Use Mutiny operators like `.onItem().transform()` and `.subscribe().with()` for reactive composition. Avoid blocking calls in subscriber handlers.

### Integration with ConnectionRegistry

**IMPORTANT:** You MUST modify ConnectionRegistry to call your subscription management methods. Here's the recommended integration approach:

1. Add `@Inject RoomEventSubscriber eventSubscriber;` to ConnectionRegistry
2. In `addConnection(roomId, session)`, after adding to the map, check if this is the FIRST connection for this room: `if (roomConnections.get(roomId).size() == 1) { eventSubscriber.subscribeToRoom(roomId); }`
3. In `removeConnection(session)`, after removing from map, check if room is now EMPTY: `if (sessions.isEmpty()) { eventSubscriber.unsubscribeFromRoom(roomId); }`

This ensures subscription lifecycle is tightly coupled to actual WebSocket connection state, which is exactly what the architecture requires.

### Package Organization

*   Your new classes belong in `backend/src/main/java/com/scrumpoker/event/`
*   The package already exists with a `package-info.java` documenting its purpose
*   Follow existing naming conventions: service classes use noun names (RoomEventPublisher, RoomEventSubscriber)
*   Use `@ApplicationScoped` for CDI beans that should be singletons

### Reactive Programming Patterns

**Example pattern for publishing:**
```java
@Inject
ReactiveRedisDataSource redisDataSource;

public Uni<Void> publishEvent(String roomId, String type, Map<String, Object> payload) {
    String channel = "room:" + roomId;
    RoomEvent event = new RoomEvent(type, payload);
    String json = objectMapper.writeValueAsString(event);
    return redisDataSource.pubsub(String.class).publish(channel, json);
}
```

**Example pattern for subscribing:**
```java
public void subscribeToRoom(String roomId) {
    String channel = "room:" + roomId;
    Multi<String> subscription = redisDataSource.pubsub(String.class).subscribe(channel);

    subscription.subscribe().with(
        message -> handleReceivedMessage(roomId, message),
        failure -> Log.errorf(failure, "Redis subscription error for room %s", roomId)
    );
}
```

---

## Summary Checklist for Coder Agent

Before you start coding, ensure you understand:

- [x] Redis Pub/Sub channel naming convention: `room:{roomId}`
- [x] ConnectionRegistry provides `broadcastToRoom()` for local WebSocket delivery
- [x] Subscription lifecycle MUST be managed based on first-join/last-leave events
- [x] Use Quarkus reactive Redis client with Mutiny for non-blocking operations
- [x] RoomEvent class should match WebSocketMessage structure for consistency
- [x] Integration requires modifying ConnectionRegistry to call subscription methods
- [x] JSON serialization using injected ObjectMapper instance
- [x] Thread-safe concurrent data structures for tracking active subscriptions
- [x] Error handling for Redis failures should be graceful (log, don't crash)

**Next Steps:**
1. Create `RoomEvent.java` data class
2. Implement `RoomEventPublisher.java` with `publishEvent()` method
3. Implement `RoomEventSubscriber.java` with subscription lifecycle management
4. Modify `ConnectionRegistry.java` to integrate subscription hooks
5. Write integration tests validating pub/sub flow

Good luck! Remember to follow the reactive programming patterns and ensure thread safety throughout.
