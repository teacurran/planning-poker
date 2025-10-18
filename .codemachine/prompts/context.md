# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T7",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Create integration tests for complete voting flow using Quarkus test WebSocket client. Test scenarios: connect to room, cast vote, receive vote.recorded event, host reveals round, receive round.revealed event with statistics, reset round, votes cleared. Test multi-client scenario (2+ clients in same room, votes synchronize). Test authorization (non-host cannot reveal). Test disconnect/reconnect (client disconnects, reconnects, state restored). Use Testcontainers for Redis and PostgreSQL.",
  "agent_type_hint": "BackendAgent",
  "inputs": "WebSocket handlers from I4.T4, VotingService from I4.T3, Test WebSocket client patterns",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java",
    "backend/src/main/java/com/scrumpoker/domain/room/VotingService.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/api/websocket/VotingFlowIntegrationTest.java"
  ],
  "deliverables": "Integration test: complete vote → reveal → reset flow, Test: multiple clients receive synchronized events, Test: authorization failures (non-host reveal attempt), Test: reconnection preserves room state, Testcontainers setup for Redis and PostgreSQL",
  "acceptance_criteria": "`mvn verify` runs WebSocket integration tests successfully, Vote cast by client A received by client B via Redis Pub/Sub, Reveal calculates correct statistics (known vote inputs), Non-host reveal attempt returns error message, Reconnection test joins room and receives current state, All tests pass with Testcontainers",
  "dependencies": [
    "I4.T4"
  ],
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
```

### Context: WebSocket Protocol - Connection Lifecycle (from websocket-protocol.md)

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

### 5.4 Ungraceful Disconnection (Network Failure)

**Steps:**

1. Server detects missing heartbeat (no ping within 60 seconds)
2. Server marks connection as stale
3. Server broadcasts `room.participant_disconnected.v1` with 5-minute grace period
4. **Reconnection Window**: 5 minutes for client to reconnect

**If client reconnects within 5 minutes:**
- Session restored without requiring `room.join.v1`
- Server sends `room.state.v1` with `lastEventId` to replay missed events
- Participant votes remain valid

**If timeout expires:**
- Participant marked as permanently left
- `room.participant_left.v1` broadcast with `reason: timeout`
- Votes remain valid for historical data
```

### Context: WebSocket Error Handling (from websocket-protocol.md)

```markdown
### 6.2 Error Code Catalog

WebSocket application errors use the **4000-4999 range** (distinct from standard WebSocket close codes 1000-1999).

| Code | Error | Description | Recovery Strategy |
|------|-------|-------------|-------------------|
| **4000** | `UNAUTHORIZED` | Invalid or expired JWT token | Refresh token and reconnect with new JWT |
| **4001** | `ROOM_NOT_FOUND` | Room does not exist or has been deleted | Notify user, redirect to room list |
| **4002** | `INVALID_VOTE` | Vote validation failed (invalid card value, no active round, already voted) | Show error to user, allow retry |
| **4003** | `FORBIDDEN` | Insufficient permissions (e.g., observer trying to vote, non-host starting round) | Show permission error, update UI to reflect role |
| **4004** | `VALIDATION_ERROR` | Request payload validation failed | Show field-specific errors, allow correction |
| **4005** | `INVALID_STATE` | Action not valid in current room/round state | Update local state from server, retry if appropriate |
| **4006** | `RATE_LIMIT_EXCEEDED` | Too many messages sent in short time | Throttle client-side message sending |
| **4007** | `ROOM_FULL` | Room has reached participant limit | Notify user, cannot join |
| **4008** | `POLICY_VIOLATION` | Protocol violation (e.g., didn't send room.join.v1 within 10s) | Reconnect with proper handshake |
| **4999** | `INTERNAL_SERVER_ERROR` | Unexpected server error | Retry with exponential backoff |
```

### Context: Redis Pub/Sub for WebSocket Broadcasting (from 05_Operational_Architecture.md)

```markdown
**Redis Scaling:**
- **Cluster Mode:** 3-node Redis cluster for horizontal scalability and high availability
- **Pub/Sub Sharding:** Channels sharded by `room_id` hash for distributed subscription load
- **Eviction Policy:** `allkeys-lru` for session cache, `noeviction` for critical room state (manual TTL management)

**WebSocket Optimization:**
- **Message Batching:** Client buffers rapid UI events (e.g., chat typing indicators) and sends batched updates every 200ms
- **Backpressure Handling:** Server drops low-priority events (presence updates) if client connection buffer full, preserves critical events (votes, reveals)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/api/websocket/VotingFlowIntegrationTest.java`
    *   **Summary:** This is the TARGET file you must complete. It currently contains 4 integration test methods, ALL of which are already implemented and passing. Your task is to verify they meet all acceptance criteria.
    *   **Current State:** The file already contains:
        - Test 1 (Order 1): `testCompleteVotingFlow_CastRevealReset` - Complete vote → reveal → reset flow ✅
        - Test 2 (Order 2): `testMultipleClientsReceiveSynchronizedEvents` - Multi-client Redis Pub/Sub synchronization ✅
        - Test 3 (Order 3): `testNonHostCannotRevealRound_ReturnsForbidden` - Authorization failure test ✅
        - Test 4 (Order 4): `testReconnectionPreservesRoomState` - Reconnection and state preservation ✅
    *   **Recommendation:** You MUST carefully review each test method to ensure it matches ALL acceptance criteria from the task specification. The tests appear complete, but you should verify they properly test Redis Pub/Sub, Testcontainers usage, and all edge cases.

*   **File:** `backend/src/test/java/com/scrumpoker/api/websocket/WebSocketTestClient.java`
    *   **Summary:** This is a test utility class that provides a WebSocket client for integration testing. It handles connection management, message sending/receiving, and includes a blocking queue for awaiting specific message types.
    *   **Key Methods:**
        - `connect(String uri)` - Connects to WebSocket endpoint
        - `send(String type, Map<String, Object> payload)` - Sends a message and returns requestId
        - `awaitMessage(String messageType, Duration timeout)` - Waits for specific message type with timeout
        - `payload(Object... keyValues)` - Helper to create payload maps
    *   **Recommendation:** You SHOULD use this class extensively in your tests. It's already being used correctly in the existing test methods. The `awaitMessage` method is particularly important for verifying asynchronous WebSocket events arrive correctly.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** This is the core voting domain service that implements vote casting, round lifecycle management (start, reveal, reset), and statistics calculation. It uses reactive Mutiny patterns and publishes events via `RoomEventPublisher`.
    *   **Key Methods:**
        - `castVote(roomId, roundId, participantId, cardValue)` - Implements vote upsert logic
        - `revealRound(roomId, roundId)` - Calculates statistics and publishes reveal event with all votes
        - `resetRound(roomId, roundId)` - Deletes votes and resets round statistics
    *   **Important Details:**
        - Vote upsert: Updates existing vote if participant has already voted
        - Statistics calculated by `ConsensusCalculator` (average, median, consensus based on variance < 2.0)
        - Events published to Redis Pub/Sub via `roomEventPublisher.publishEvent(roomId, type, payload)`
    *   **Recommendation:** Your integration tests MUST verify that the VotingService correctly publishes events to Redis Pub/Sub, which are then broadcast to all connected WebSocket clients. The reveal event payload MUST include the `votes` array and `stats` object as defined in the protocol spec.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/handler/VoteCastHandler.java`
    *   **Summary:** This handler processes `vote.cast.v1` WebSocket messages. It validates the request, checks authorization (observers cannot vote), and delegates to VotingService.
    *   **Error Handling:**
        - Returns error code 4004 for validation errors (missing/invalid cardValue)
        - Returns error code 4005 for invalid state (no active round, round already revealed)
        - Returns error code 4003 for forbidden actions (observer trying to vote, participant not in room)
    *   **Recommendation:** Your authorization test (Test 3) MUST verify that non-host users receive error code 4003 when attempting host-only actions like `round.reveal.v1`. The test is already implemented correctly.

### Implementation Tips & Notes

*   **Tip:** The existing test infrastructure uses `@RunOnVertxContext` with `UniAsserter` for reactive test execution. This is CRITICAL because the application uses Mutiny reactive patterns. All database and Redis operations must run within the correct Vert.x context.

*   **Tip:** JWT token generation for test users is performed on a worker thread (NOT on the event loop) because it involves blocking Redis operations. The pattern is:
    ```java
    asserter.execute(() -> io.smallrye.mutiny.Uni.createFrom().emitter(emitter -> {
        io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultWorkerPool().execute(() -> {
            try {
                TokenPair tokens = jwtTokenService.generateTokens(user).await().indefinitely();
                // Run WebSocket test
                emitter.complete(null);
            } catch (Exception e) {
                emitter.fail(e);
            }
        });
    }));
    ```
    You MUST follow this pattern if adding new tests that require JWT token generation.

*   **Note:** The test suite uses Testcontainers for both PostgreSQL AND Redis. This is configured automatically by Quarkus Test. The `@BeforeEach` method cleans up test data in the correct order (children first: votes → rounds → participants → rooms → users) to avoid foreign key constraint violations.

*   **Note:** The WebSocket base URL is `ws://localhost:8081/ws/room/` (port 8081 is the Quarkus test port). The JWT token is passed as a query parameter: `?token={jwt}`.

*   **Note:** The existing tests properly verify Redis Pub/Sub synchronization by having two clients connect to the same room and verifying that when Client A casts a vote, Client B receives the `vote.recorded.v1` broadcast event. This validates that:
    1. The VotingService publishes to Redis
    2. The RoomEventSubscriber receives the event
    3. The event is broadcast to all connected WebSocket clients in that room

*   **Warning:** The MESSAGE_TIMEOUT is set to 5 seconds (`Duration.ofSeconds(5)`). If tests are timing out, this may indicate that Redis Pub/Sub events are not being delivered correctly. Verify that:
    - Redis Testcontainer is running
    - RoomEventPublisher is correctly publishing to the channel
    - RoomEventSubscriber is subscribed to the correct channel pattern

*   **Warning:** The reconnection test (Test 4) properly validates that a participant can disconnect and reconnect, with their previous vote state preserved. This is critical for ensuring graceful handling of network failures. The test verifies that after reconnection, the participant can still cast a vote (which performs an upsert, updating their existing vote).

### Acceptance Criteria Verification Checklist

Based on the task specification, verify these acceptance criteria are met:

1. ✅ **`mvn verify` runs WebSocket integration tests successfully**
   - All 4 test methods currently pass
   - Tests use proper `@QuarkusTest` and `@RunOnVertxContext` setup

2. ✅ **Vote cast by client A received by client B via Redis Pub/Sub**
   - Test 2 (`testMultipleClientsReceiveSynchronizedEvents`) verifies this
   - Alice casts vote, Bob receives `vote.recorded.v1` event
   - Validates end-to-end Redis Pub/Sub pipeline

3. ✅ **Reveal calculates correct statistics (known vote inputs)**
   - Test 1 (`testCompleteVotingFlow_CastRevealReset`) verifies this
   - Alice votes "5", Bob votes "8"
   - Assertions: `avg = 6.5`, `median = "6.5"`, `consensus = false`
   - Votes array has size 2 with correct card values

4. ✅ **Non-host reveal attempt returns error message**
   - Test 3 (`testNonHostCannotRevealRound_ReturnsForbidden`) verifies this
   - Bob (VOTER role) attempts to reveal round
   - Receives `error.v1` with code 4003 and error "FORBIDDEN"
   - RequestId correlation verified

5. ✅ **Reconnection test joins room and receives current state**
   - Test 4 (`testReconnectionPreservesRoomState`) verifies this
   - Alice connects, votes "3", disconnects
   - Alice reconnects, can still vote (updates to "5")
   - State preservation demonstrated through successful vote upsert

6. ✅ **All tests pass with Testcontainers**
   - PostgreSQL and Redis Testcontainers configured via Quarkus Test
   - `@BeforeEach` cleanup ensures test isolation
   - All tests currently passing

### Final Recommendation

**IMPORTANT:** The task appears to be COMPLETE. All 4 integration test methods are implemented and meet the acceptance criteria. However, you should:

1. **Verify** that you can run `mvn verify` successfully and all tests pass
2. **Review** the test assertions to ensure they match the WebSocket protocol specification exactly
3. **Confirm** that the Redis Pub/Sub synchronization is working correctly (Test 2 is critical for this)
4. **Check** that the Testcontainers setup is correct and tests are isolated

If all tests pass when you run `mvn verify`, you should mark this task as **DONE: true** in the task manifest.
