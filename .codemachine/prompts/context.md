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
  "inputs": "*   WebSocket protocol specification from I2.T2\n        *   WebSocket connection lifecycle from architecture blueprint\n        *   JWT authentication from I3",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   WebSocket endpoint with JWT-based authentication\n        *   Connection registry managing active sessions per room\n        *   Heartbeat mechanism (server sends ping, expects pong within 60 seconds)\n        *   Participant joined/left event broadcasting\n        *   Error handling and graceful disconnection",
  "acceptance_criteria": "*   WebSocket connection succeeds with valid JWT token\n        *   Connection rejected with 401 if token invalid/missing\n        *   Participant joined event broadcasted to existing room connections\n        *   Heartbeat mechanism prevents stale connections (auto-close after timeout)\n        *   Connection gracefully closed on client disconnect\n        *   Multiple clients can connect to same room simultaneously",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Iteration 4: Real-Time Voting Engine (WebSocket) (from .codemachine/artifacts/plan/02_Iteration_I4.md)

```markdown
<!-- anchor: iteration-4 -->
### Iteration 4: Real-Time Voting Engine (WebSocket)

*   **Iteration ID:** `I4`

*   **Goal:** Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.

*   **Prerequisites:** I2 (RoomService, Room entity), I3 (Authentication, JWT validation)

*   **Tasks:**
```

### Context: Task 4.1: Implement WebSocket Connection Handler (from .codemachine/artifacts/plan/02_Iteration_I4.md)

```markdown
<!-- anchor: task-i4-t1 -->
*   **Task 4.1: Implement WebSocket Connection Handler**
    *   **Task ID:** `I4.T1`
    *   **Description:** Create `RoomWebSocketHandler` using Quarkus WebSocket extension. Implement endpoint `/ws/room/{roomId}` with JWT authentication on handshake (validate token from query parameter `?token={jwt}`). Manage connection lifecycle: onOpen (validate room exists, validate user authorized, subscribe to Redis channel `room:{roomId}`, broadcast `participant_joined` event), onClose (unsubscribe, broadcast `participant_left`), onMessage (route to message handlers), onError (log, close gracefully). Store active connections in ConcurrentHashMap keyed by room ID. Implement heartbeat protocol (ping/pong every 30 seconds).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   WebSocket protocol specification from I2.T2
        *   WebSocket connection lifecycle from architecture blueprint
        *   JWT authentication from I3
    *   **Input Files:**
        *   `api/websocket-protocol.md`
        *   `.codemachine/artifacts/architecture/04_Behavior_and_Communication.md` (WebSocket section)
        *   `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
        *   `backend/src/main/java/com/scrumpoker/api/websocket/WebSocketMessage.java` (envelope DTO)
    *   **Deliverables:**
        *   WebSocket endpoint with JWT-based authentication
        *   Connection registry managing active sessions per room
        *   Heartbeat mechanism (server sends ping, expects pong within 60 seconds)
        *   Participant joined/left event broadcasting
        *   Error handling and graceful disconnection
    *   **Acceptance Criteria:**
        *   WebSocket connection succeeds with valid JWT token
        *   Connection rejected with 401 if token invalid/missing
        *   Participant joined event broadcasted to existing room connections
        *   Heartbeat mechanism prevents stale connections (auto-close after timeout)
        *   Connection gracefully closed on client disconnect
        *   Multiple clients can connect to same room simultaneously
    *   **Dependencies:** [I2.T3, I3.T2]
    *   **Parallelizable:** No (depends on RoomService and JWT)
```

### Context: Asynchronous WebSocket (Event-Driven) (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

```markdown
<!-- anchor: asynchronous-websocket-pattern -->
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

### Context: WebSocket Connection Lifecycle (from .codemachine/artifacts/architecture/04_Behavior_and_Communication.md)

```markdown
<!-- anchor: websocket-connection-lifecycle -->
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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Summary:** Implements the `/ws/room/{roomId}` endpoint with Vert.x-friendly async validation: extracts the `token` query param, validates it through `JwtTokenService`, looks up the room via `RoomService`, then stores `userId`, `roomId`, and correlation metadata inside the `Session`. It registers each `Session` in `ConnectionRegistry`, enforces that `room.join.v1` arrives within 10 seconds via `pendingJoins`, pushes inbound JSON through `MessageRouter`, and wires scheduled heartbeat, stale-session cleanup, and join-timeout enforcement jobs.
    *   **Recommendation:** Whenever you add lifecycle logic, piggyback on the provided helpers (`scheduleJoinTimeout`, `connectionRegistry`, `sendError`, MDC utilities) so correlation IDs and reactive flows remain consistent. All outbound frames should be emitted through `ConnectionRegistry` to keep Redis synchronization and metrics accurate.
*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/ConnectionRegistry.java`
    *   **Summary:** Maintains concurrent maps of `roomId → Set<Session>` plus reverse lookups and heartbeat timestamps. Automatically subscribes/unsubscribes Redis channels through `RoomEventSubscriber` as the first/last connection joins or leaves, and provides `broadcastToRoom`, `sendToSession`, `getStaleSessions`, and `updateLastPong` helpers.
    *   **Recommendation:** Never mutate raw `Session` collections yourself—use `addConnection`/`removeConnection` so Redis subscriptions and heartbeat tracking stay in sync. Use `broadcastToRoom` for participant join/leave notifications instead of iterating sessions manually.
*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/WebSocketMessage.java`
    *   **Summary:** Defines the canonical envelope used everywhere, plus builders for error, `participant_joined`, and `participant_left` payloads. Ensures Jackson serializes fields as expected by `api/websocket-protocol.md`.
    *   **Recommendation:** Build all server-originated events via these factory methods (or add new ones) so message types, UUID handling, and payload structures never drift from the protocol.
*   **File:** `backend/src/main/java/com/scrumpoker/security/JwtTokenService.java`
    *   **Summary:** Provides `Uni<JwtClaims> validateAccessToken(String)` used during the handshake, along with token generation/refresh utilities. Claims expose `userId`, `email`, roles, and tier, and failures are surfaced via the reactive pipeline so `RoomWebSocketHandler` can send 4000-series errors.
    *   **Recommendation:** Keep all authentication checks asynchronous by chaining off this service rather than blocking for validation; propagate `JwtClaims` into session properties so later message handlers can authorize host-only actions without refetching tokens.

### Implementation Tips & Notes
*   **Tip:** The WebSocket protocol spec at `api/websocket-protocol.md` enumerates every allowed message type and error code—mirror those names/codes when emitting `WebSocketMessage` instances so frontend clients can rely on standardized enums.
*   **Tip:** Join enforcement already schedules a POLICIES_VIOLATION close (code 4008) if `room.join.v1` never arrives; call `cancelJoinTimeout(session)` as soon as you process a valid join to prevent accidental disconnects when handling large payloads.
*   **Tip:** Heartbeats rely on `ConnectionRegistry.updateLastPong` and `getStaleSessions`; if you tweak ping frequency or timeout, update `HEARTBEAT_INTERVAL_SECONDS`/`HEARTBEAT_TIMEOUT_SECONDS` in `RoomWebSocketHandler` so the scheduled jobs stay aligned with the architecture requirements (30s ping, 60s cutoff).
*   **Note:** `ConnectionRegistry` injects `RoomEventSubscriber`, so publishing/subscribing to Redis channels happens automatically the first time a room sees a connection. When you introduce cross-node broadcast handlers, register them through the subscriber rather than ad-hoc Redis calls.
*   **Note:** `MessageRouter` (in `backend/src/main/java/com/scrumpoker/api/websocket/MessageRouter.java`) already centralizes dispatch to typed handlers—feed raw JSON into it via `handleMessage` to keep validation, metrics, and error mapping consistent.
*   **Warning:** Although `RoomWebSocketHandler` currently allows anonymous sessions (when `token` is missing) by generating `anon_*` IDs, product requirements still expect JWT-authenticated access for private rooms. Verify with product before expanding anonymous capabilities, and ensure unauthorized sessions never bypass `RoomService` access checks.
