# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T1",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Create `RoomWebSocketHandler` using Quarkus WebSocket extension. Implement endpoint `/ws/room/{roomId}` with JWT authentication on handshake (validate token from query parameter `?token={jwt}`). Manage connection lifecycle: onOpen (validate room exists, validate user authorized, subscribe to Redis channel `room:{roomId}`, broadcast `participant_joined` event), onClose (unsubscribe, broadcast `participant_left`), onMessage (route to message handlers), onError (log, close gracefully). Store active connections in ConcurrentHashMap keyed by room ID. Implement heartbeat protocol (ping/pong every 30 seconds).",
  "agent_type_hint": "BackendAgent",
  "inputs": "WebSocket protocol specification from I2.T2, WebSocket connection lifecycle from architecture blueprint, JWT authentication from I3",
  "input_files": [
    "api/websocket-protocol.md",
    ".codemachine/artifacts/architecture/04_Behavior_and_Communication.md",
    "backend/src/main/java/com/scrumpoker/security/JwtTokenService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java",
    "backend/src/main/java/com/scrumpoker/api/websocket/WebSocketMessage.java"
  ],
  "deliverables": "WebSocket endpoint with JWT-based authentication, Connection registry managing active sessions per room, Heartbeat mechanism (server sends ping, expects pong within 60 seconds), Participant joined/left event broadcasting, Error handling and graceful disconnection",
  "acceptance_criteria": "WebSocket connection succeeds with valid JWT token, Connection rejected with 401 if token invalid/missing, Participant joined event broadcasted to existing room connections, Heartbeat mechanism prevents stale connections (auto-close after timeout), Connection gracefully closed on client disconnect, Multiple clients can connect to same room simultaneously",
  "dependencies": [
    "I2.T3",
    "I3.T2"
  ],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: WebSocket Connection Lifecycle (from 04_Behavior_and_Communication.md)

```markdown
#### WebSocket Connection Lifecycle

**Connection Establishment:**
1. Client initiates WebSocket handshake: `wss://api.scrumpoker.com/ws/room/{roomId}?token={jwt}`
2. Server validates JWT token, extracts user/participant identity
3. Server checks room existence and user authorization (privacy mode enforcement)
4. Server subscribes connection to Redis Pub/Sub channel: `room:{roomId}`
5. Server broadcasts `room.participant_joined.v1` event to existing participants
6. Server sends initial room state snapshot to newly connected client

**Heartbeat Protocol:**
- Client sends `ping` frame every 30 seconds
- Server responds with `pong` frame
- Connection terminated if no `ping` received within 60 seconds (2x interval)

**Graceful Disconnection:**
1. Client sends `room.leave.v1` message before closing connection
2. Server persists disconnection timestamp in `RoomParticipant` table
3. Server broadcasts `room.participant_left.v1` to remaining participants
4. Server unsubscribes from Redis channel if no more local connections to room

**Ungraceful Disconnection (Network Failure):**
1. Server detects missing heartbeat, marks connection as stale
2. Server broadcasts `room.participant_disconnected.v1` with grace period
3. If client reconnects within 5 minutes, restores session without re-join
4. If timeout expires, participant marked as left, votes remain valid

**Reconnection Strategy (Client-Side):**
- Detect connection loss via WebSocket `onclose` event
- Attempt reconnection with exponential backoff: 1s, 2s, 4s, 8s, 16s (max)
- Include `lastEventId` in reconnection handshake to retrieve missed events
- Server replays events from Redis or database within 5-minute window
```

### Context: Message Envelope Format (from websocket-protocol.md)

```markdown
## 2. Message Envelope Format

All WebSocket messages (both client→server and server→client) use a standardized JSON envelope structure.

### 2.1 Envelope Structure

{
  "type": "message_type.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    // Message-specific payload
  }
}

### 2.2 Envelope Fields

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Versioned message type following pattern: `entity.action.version` (e.g., `vote.cast.v1`) |
| `requestId` | string (UUID v4) | Yes | Unique request identifier for request/response correlation. Clients generate UUIDs for requests; server echoes same ID in responses/broadcasts. |
| `payload` | object | Yes | Message-specific data. Schema depends on message type. May be empty object `{}` for some message types. |
```

### Context: Connection Establishment Flow (from websocket-protocol.md)

```markdown
### 5.1 Connection Establishment

**Steps:**

1. **WebSocket Handshake**
   - Client initiates: `wss://api.planningpoker.example.com/ws/room/{roomId}?token={jwt}`
   - Server validates JWT token, extracts user/participant identity
   - Server checks room existence and user authorization (privacy mode enforcement)

2. **Server Setup**
   - Server subscribes connection to Redis Pub/Sub channel: `room:{roomId}`
   - WebSocket connection established (HTTP 101 Switching Protocols)

3. **Room Join**
   - Client **MUST** send `room.join.v1` message immediately after connection
   - Server validates join request, creates/updates `RoomParticipant` record
   - Server broadcasts `room.participant_joined.v1` event to existing participants
   - Server sends `room.state.v1` (initial state snapshot) to newly connected client

**Timeout:** If client does not send `room.join.v1` within 10 seconds, server closes connection with code 4008 (policy violation).
```

### Context: Error Code Catalog (from websocket-protocol.md)

```markdown
### 6.2 Error Code Catalog

WebSocket application errors use the **4000-4999 range** (distinct from standard WebSocket close codes 1000-1999).

| Code | Error | Description | Recovery Strategy |
|------|-------|-------------|-------------------|
| **4000** | `UNAUTHORIZED` | Invalid or expired JWT token | Refresh token and reconnect with new JWT |
| **4001** | `ROOM_NOT_FOUND` | Room does not exist or has been deleted | Notify user, redirect to room list |
| **4008** | `POLICY_VIOLATION` | Protocol violation (e.g., didn't send room.join.v1 within 10s) | Reconnect with proper handshake |
```

### Context: Asynchronous WebSocket Pattern (from 04_Behavior_and_Communication.md)

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
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** This service provides comprehensive JWT token lifecycle management including token generation, validation with signature verification, and refresh token rotation. It uses SmallRye JWT with RS256 signing and stores refresh tokens in Redis.
    *   **Recommendation:** You MUST import and use the `validateAccessToken(String token)` method from this service to validate JWT tokens during WebSocket handshake. This method returns a `Uni<JwtClaims>` containing userId, email, roles, and tier. You SHOULD extract the userId from the JwtClaims to identify the user attempting to connect.
    *   **Key Methods:**
        - `validateAccessToken(String token)` - Validates JWT signature, expiration, and extracts claims
        - Returns `Uni<JwtClaims>` with fields: userId (UUID), email, roles (List<String>), tier (String)

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** Domain service for room management operations including room CRUD with business validation, JSONB config handling, and reactive return types. Uses RoomRepository for database operations.
    *   **Recommendation:** You MUST use the `findById(String roomId)` method to validate that the room exists before allowing WebSocket connections. This method returns `Uni<Room>` and throws `RoomNotFoundException` if the room doesn't exist or is soft-deleted.
    *   **Key Details:**
        - Room IDs are 6-character nanoids (e.g., "abc123"), NOT UUIDs
        - Rooms can be soft-deleted (deletedAt field), and findById excludes deleted rooms
        - RoomService uses reactive programming (returns Uni/Multi)

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** JPA entity representing an estimation session with 6-character nanoid identifier. Supports soft delete via deleted_at timestamp.
    *   **Recommendation:** The Room entity has a `roomId` field that is a String (6-character nanoid), NOT a UUID. When validating room existence in the WebSocket handler, use the roomId from the path parameter directly.
    *   **Key Fields:**
        - `roomId` (String) - 6-character nanoid primary key
        - `owner` (User) - Nullable for anonymous rooms
        - `privacyMode` (PrivacyMode enum) - PUBLIC, INVITE_ONLY, ORG_RESTRICTED
        - `deletedAt` (Instant) - Soft delete timestamp

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven POM file with Quarkus 3.15.1 and all required dependencies including quarkus-websockets, quarkus-redis-client, quarkus-smallrye-jwt, and quarkus-hibernate-reactive-panache.
    *   **Recommendation:** The project already has the `quarkus-websockets` dependency configured. You SHOULD use Quarkus' WebSocket annotations (`@ServerEndpoint`, `@OnOpen`, `@OnClose`, `@OnMessage`, `@OnError`) to implement the WebSocket handler.
    *   **Note:** The project uses Java 17 and reactive programming patterns throughout (Mutiny Uni/Multi).

### Implementation Tips & Notes

*   **Tip:** For JWT validation during WebSocket handshake, you MUST extract the token from the query parameter `?token={jwt}`. Quarkus WebSockets do not automatically support JWT authentication like REST endpoints do. You will need to manually parse the query string in the `@OnOpen` method to extract the token parameter.

*   **Tip:** The WebSocket protocol specification requires a heartbeat mechanism using native WebSocket ping/pong frames (NOT custom JSON messages). Quarkus WebSockets support this via the `Session.getAsyncRemote().sendPing()` method. You SHOULD implement a scheduled task that sends ping frames every 30 seconds to all active connections and tracks the last pong received to detect stale connections.

*   **Note:** The connection registry (ConcurrentHashMap) MUST be thread-safe as multiple WebSocket connections will be accessing it concurrently. Consider using `ConcurrentHashMap<String, Set<Session>>` where the key is the roomId and the value is a set of active WebSocket sessions for that room. Use `ConcurrentHashMap.newKeySet()` to create thread-safe sets.

*   **Warning:** WebSocket connections in Quarkus are NOT CDI request-scoped. The `@OnOpen`, `@OnClose`, `@OnMessage`, and `@OnError` methods will be called by Quarkus' WebSocket infrastructure, and you will NOT have automatic dependency injection in these methods. You MUST use `CDI.current().select(ServiceClass.class).get()` to manually look up CDI beans (like JwtTokenService, RoomService) inside these methods.

*   **Tip:** For error handling, the WebSocket protocol specifies custom error codes in the 4000-4999 range. When closing a WebSocket connection due to an error, use `session.close(new CloseReason(CloseReason.CloseCodes.PROTOCOL_ERROR, "error message"))` for protocol violations, or define custom close codes. The protocol specifies code 4000 for UNAUTHORIZED, 4001 for ROOM_NOT_FOUND, and 4008 for POLICY_VIOLATION.

*   **Note:** Redis Pub/Sub integration is required for broadcasting events across multiple application nodes (for horizontal scaling). However, this is the responsibility of Task I4.T2 (RoomEventPublisher/Subscriber). For THIS task (I4.T1), you only need to prepare the connection registry and basic message handling infrastructure. Do NOT implement Redis Pub/Sub yet - just ensure the architecture supports it (e.g., by having a method to broadcast messages to all connections in a room).

*   **Warning:** The WebSocket endpoint path is `/ws/room/{roomId}` where `{roomId}` is a path parameter. You MUST use the `@PathParam` annotation to extract this parameter in your `@ServerEndpoint` class. The endpoint should be annotated like: `@ServerEndpoint("/ws/room/{roomId}")`.

*   **Tip:** For the participant joined/left events, you need to create basic message envelope objects following the protocol specification (type, requestId, payload structure). Create a `WebSocketMessage` class with these fields that can be serialized to JSON using Jackson. The protocol requires that all messages have a `type` field (e.g., "room.participant_joined.v1"), a `requestId` (UUID), and a `payload` object.

*   **Important:** The protocol specifies that clients MUST send a `room.join.v1` message immediately after connection (within 10 seconds). For THIS task, you should implement a timeout mechanism that closes the connection if no join message is received within 10 seconds. However, the actual handling of the `room.join.v1` message (creating RoomParticipant records, broadcasting to other participants) will be implemented in Task I4.T4 (message handlers). For now, just implement the timeout and basic message routing infrastructure.

### Quarkus WebSocket Specific Notes

*   **Quarkus WebSocket Lifecycle:** Quarkus WebSocket endpoints are NOT standard JAX-RS resources. They use a different lifecycle:
    - `@ServerEndpoint` marks the class as a WebSocket endpoint
    - `@OnOpen` is called when a connection is established
    - `@OnMessage` is called when a message is received
    - `@OnClose` is called when the connection is closed
    - `@OnError` is called when an error occurs

*   **Session Management:** The `Session` object represents a single WebSocket connection. It provides methods to:
    - Send text messages: `session.getAsyncRemote().sendText(message)`
    - Send ping frames: `session.getAsyncRemote().sendPing()`
    - Close the connection: `session.close(closeReason)`
    - Get query parameters: `session.getRequestParameterMap().get("token")`

*   **CDI Integration:** To inject CDI beans in a WebSocket endpoint, you must make the endpoint class `@ApplicationScoped` and use constructor injection. Alternatively, use `CDI.current().select(ServiceClass.class).get()` to manually look up beans in the lifecycle methods.

### Recommended Implementation Structure

1. **Create `WebSocketMessage.java`** - Basic envelope class with type, requestId, payload fields
2. **Create `ConnectionRegistry.java`** - Thread-safe registry managing active connections per room
3. **Create `RoomWebSocketHandler.java`** - Main WebSocket endpoint class with:
   - `@ServerEndpoint("/ws/room/{roomId}")` annotation
   - `@OnOpen` method: validate JWT, validate room exists, register connection, schedule join timeout
   - `@OnClose` method: unregister connection, broadcast participant_left (basic)
   - `@OnMessage` method: parse message, route to handlers (placeholder for now)
   - `@OnError` method: log error, close connection gracefully
   - Heartbeat mechanism: scheduled task sending pings, tracking last pong received

### Security Considerations

*   **CRITICAL:** You MUST validate the JWT token before allowing any WebSocket operations. If the token is invalid or expired, close the connection immediately with code 4000 (UNAUTHORIZED).
*   **IMPORTANT:** Validate that the room exists and is not deleted before allowing connections. If the room is not found, close with code 4001 (ROOM_NOT_FOUND).
*   **Note:** Privacy mode validation (INVITE_ONLY, ORG_RESTRICTED) should be implemented, but may be deferred to a later task if time is limited. At minimum, validate that PUBLIC rooms are accessible to any authenticated user.

---

**End of Task Briefing Package**
