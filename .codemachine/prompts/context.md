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

### Context: websocket-connection-lifecycle (from 04_Behavior_and_Communication.md)

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

### Context: WebSocket Protocol Specification (from websocket-protocol.md)

The complete WebSocket protocol specification is available in `api/websocket-protocol.md` (1608 lines). Key highlights for testing:

**Message Envelope Format:**
```json
{
  "type": "message_type.v1",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    // Message-specific payload
  }
}
```

**Error Code Catalog (4000-4999 range):**
- **4000** `UNAUTHORIZED` - Invalid or expired JWT token
- **4001** `ROOM_NOT_FOUND` - Room does not exist
- **4002** `INVALID_VOTE` - Vote validation failed
- **4003** `FORBIDDEN` - Insufficient permissions (non-host trying to reveal)
- **4004** `VALIDATION_ERROR` - Request payload validation failed
- **4005** `INVALID_STATE` - Action not valid in current room/round state
- **4008** `POLICY_VIOLATION` - Protocol violation (didn't send room.join.v1 within 10s)
- **4999** `INTERNAL_SERVER_ERROR` - Unexpected server error

**Key Message Types for Testing:**
- `vote.cast.v1` (client → server)
- `vote.recorded.v1` (server → clients, broadcast)
- `round.reveal.v1` (client → server, host only)
- `round.revealed.v1` (server → clients, broadcast with statistics)
- `round.reset.v1` (client → server, host only)
- `error.v1` (server → client, unicast error response)

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/RoomWebSocketHandler.java`
    *   **Summary:** This is the main WebSocket endpoint (`@ServerEndpoint("/ws/room/{roomId}")`) handling connection lifecycle (onOpen, onClose, onMessage, onError). It implements JWT authentication on handshake, heartbeat protocol, and message routing via MessageRouter.
    *   **Recommendation:** You MUST understand this handler's connection flow for your test. The handler validates JWT tokens from query parameter `?token={jwt}`, requires `room.join.v1` within 10 seconds, and uses ConnectionRegistry to manage sessions. Your test needs to simulate this exact connection flow.
    *   **Key Details:** `onOpen()` validates JWT and room existence, stores `userId` and `roomId` in session properties, schedules join timeout. `onMessage()` routes to MessageRouter. The handler expects specific message envelope structure with `type`, `requestId`, and `payload`.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** Domain service implementing voting operations: `castVote()`, `startRound()`, `revealRound()`, `resetRound()`. All methods use reactive `Uni<>` return types and `@WithTransaction`.
    *   **Recommendation:** You SHOULD use this service as the source of truth for expected behavior. The `castVote()` method implements upsert logic (updates existing vote if participant already voted). `revealRound()` calculates statistics using `ConsensusCalculator` and publishes `round.revealed.v1` event. Your tests must verify these published events are received by WebSocket clients.
    *   **Key Details:** Each voting operation publishes events via `RoomEventPublisher`. For example, `castVote()` publishes `vote.recorded.v1` with payload `{participantId, votedAt}`. The `revealRound()` publishes `round.revealed.v1` with full votes array and statistics `{avg, median, consensus}`.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/handler/VoteCastHandler.java`
    *   **Summary:** Message handler for `vote.cast.v1` messages. Validates card value, checks for active round, verifies participant role (observers cannot vote), and delegates to VotingService.
    *   **Recommendation:** Your tests MUST verify this handler's authorization logic. Observers trying to cast votes should receive error code 4003 (FORBIDDEN). You should also test invalid states like voting when no active round exists (error 4005).
    *   **Key Details:** Handler uses `extractString()` for payload validation, calls `VotingService.castVote()`, and sends error messages via `sendError()` helper. Error responses use `WebSocketMessage.createError()`.

*   **File:** `backend/src/main/java/com/scrumpoker/event/RoomEventPublisher.java`
    *   **Summary:** Service for publishing WebSocket events to Redis Pub/Sub channels. Uses channel naming convention `room:{roomId}`. Events are serialized as JSON and published to Redis.
    *   **Recommendation:** You MUST have Redis running via Testcontainers for events to be broadcast across multiple WebSocket clients. Your test should verify that when Client A casts a vote, Client B receives the `vote.recorded.v1` event through Redis Pub/Sub.
    *   **Key Details:** Publisher uses `ReactiveRedisDataSource` and `ReactivePubSubCommands<String>`. The `publishEvent()` method returns `Uni<Void>` that completes when event is published. Logs subscriber count reached.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** Example integration test using `@QuarkusTest`, `@RunOnVertxContext`, and `UniAsserter` for reactive tests. Uses Testcontainers automatically via Quarkus Dev Services.
    *   **Recommendation:** You SHOULD follow this exact testing pattern for your WebSocket integration test. Use `@QuarkusTest` for Testcontainers setup, `@RunOnVertxContext` for reactive methods, and `UniAsserter` for async assertions. The `setUp()` method shows proper cleanup pattern using `Panache.withTransaction(() -> repository.deleteAll())`.
    *   **Key Details:** Helper methods like `createTestUser()`, `createTestRoom()`, `createTestRound()` show how to build test data hierarchies. Tests use `.execute()` for setup and `.assertThat()` for assertions. All database operations wrapped in `Panache.withTransaction()`.

### Implementation Tips & Notes

*   **Tip:** Quarkus does not have a built-in WebSocket test client. You will need to use `jakarta.websocket.ContainerProvider` or a third-party WebSocket client library (e.g., Tyrus client). I found that other Quarkus projects use `org.eclipse.jetty.websocket:websocket-jakarta-client` for testing.

*   **Tip:** For testing WebSocket messages, you should create a helper class (e.g., `WebSocketTestClient`) that wraps the WebSocket connection, handles message serialization/deserialization using Jackson ObjectMapper, and provides methods like `send(WebSocketMessage)` and `awaitMessage(String messageType, Duration timeout)`.

*   **Note:** The WebSocket endpoint requires JWT authentication. Your test must either:
    1. Generate a valid JWT token using `JwtTokenService` (requires creating test user first), OR
    2. Mock/bypass JWT validation for tests (check if there's a test profile that disables security - I saw `NoSecurityTestProfile.java` in the test directory)

*   **Warning:** Redis Pub/Sub testing requires careful timing. When Client A casts a vote, the event is: persisted to DB → published to Redis → received by subscriber → broadcast to Client B. This is asynchronous and may take 50-200ms. Your `awaitMessage()` helper should have a reasonable timeout (e.g., 5 seconds) with polling.

*   **Tip:** For multi-client testing, you need to connect 2+ WebSocket clients to the same room. Each client needs its own `Session` object and should be managed in separate threads or async contexts. Use `UniAsserter` to orchestrate the async flow: Client A connects → Client B connects → Client A casts vote → Client B receives event.

*   **Note:** To test reconnection, you need to: 1) Establish connection, 2) Join room, 3) Close connection, 4) Wait briefly, 5) Reconnect with same JWT, 6) Verify room state restored. The protocol spec says reconnection within 5 minutes should restore session without requiring `room.join.v1` again (though the current implementation may not have this feature fully implemented - test what actually exists).

*   **Critical:** Your test file structure should follow this pattern:
    ```java
    @QuarkusTest
    class VotingFlowIntegrationTest {
        @Inject JwtTokenService jwtTokenService;
        @Inject UserRepository userRepository;
        @Inject RoomRepository roomRepository;

        @BeforeEach
        @RunOnVertxContext
        void setUp(UniAsserter asserter) {
            // Clean up test data
        }

        @Test
        @RunOnVertxContext
        void testCompleteVotingFlow(UniAsserter asserter) {
            // Create test users, room, round
            // Connect WebSocket clients
            // Cast votes
            // Reveal round
            // Verify statistics
        }
    }
    ```

*   **Warning:** The VotingService expects a Round to exist before votes can be cast. You must call `votingService.startRound()` first, or manually create a Round entity in your test setup. Otherwise, `vote.cast.v1` will fail with error 4005 (INVALID_STATE: "No active round in room").

*   **Tip:** For testing authorization (non-host cannot reveal), you need to create two participants with different roles: one with `RoomRole.HOST` and one with `RoomRole.VOTER`. The `round.reveal.v1` message from the VOTER should be rejected with error 4003 (FORBIDDEN). Check the handler implementation in `RoundRevealHandler.java` (I4.T4) to see exact authorization logic.

*   **Performance Note:** WebSocket integration tests can be slow due to Testcontainers startup (PostgreSQL + Redis). The first test in the suite may take 30-60 seconds while containers spin up. Subsequent tests reuse containers and run much faster (1-5 seconds each). Use `@TestMethodOrder(MethodOrderer.OrderAnnotation.class)` if you need specific test execution order.

*   **Best Practice:** Use descriptive test method names that explain the scenario, e.g., `testVoteCastByClientAReceivedByClientBViaRedisPubSub()`, `testNonHostCannotRevealRound_Returns403Forbidden()`, `testReconnectionPreservesRoomState()`.

*   **Maven Dependency:** You will likely need to add the WebSocket client dependency to `pom.xml`:
    ```xml
    <dependency>
        <groupId>org.eclipse.jetty.websocket</groupId>
        <artifactId>websocket-jakarta-client</artifactId>
        <scope>test</scope>
    </dependency>
    ```

*   **Helper Class Pattern:** Create a `WebSocketTestClient` helper class that:
    - Wraps `jakarta.websocket.Session`
    - Handles connection to `ws://localhost:8081/ws/room/{roomId}?token={jwt}` (Quarkus test server)
    - Serializes/deserializes JSON messages using ObjectMapper
    - Provides `awaitMessage(String type, Duration timeout)` using `CompletableFuture` or blocking queue
    - Provides `sendAndAwaitResponse(String type, Map<String, Object> payload, Duration timeout)`

*   **Critical Testing Flow:** Your main test should follow this exact sequence:
    1. Create test users (Alice as HOST, Bob as VOTER) and persist
    2. Create test room with Alice as owner
    3. Create test round (or call `votingService.startRound()`)
    4. Generate JWT tokens for Alice and Bob using `jwtTokenService`
    5. Connect Alice's WebSocket client → verify `room.participant_joined` event
    6. Connect Bob's WebSocket client → verify both clients receive Bob's join event
    7. Alice sends `vote.cast.v1` with cardValue "5"
    8. Verify both clients receive `vote.recorded.v1` for Alice
    9. Bob sends `vote.cast.v1` with cardValue "8"
    10. Verify both clients receive `vote.recorded.v1` for Bob
    11. Alice (host) sends `round.reveal.v1`
    12. Verify both clients receive `round.revealed.v1` with votes and statistics
    13. Assert statistics: average=6.5, median=6.5, consensus=false (variance > threshold)

---

**END OF TASK BRIEFING PACKAGE**
