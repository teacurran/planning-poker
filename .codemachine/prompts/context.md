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

### Context: WebSocket Communication Patterns (from 04_Behavior_and_Communication.md)

The architecture defines three key communication patterns. For this task, the **Asynchronous WebSocket Pattern** is critical:

**Pattern:** Client connects to WebSocket endpoint `/ws/room/{roomId}`, server validates JWT, subscribes connection to Redis Pub/Sub channel `room:{roomId}`. When events occur (vote cast, round reveal), server publishes to Redis channel. All subscribed application nodes receive event and forward to their local WebSocket connections.

**Key Requirements:**
- Vote casting: Client sends `vote.cast.v1` → Server persists → Publish `vote.recorded.v1` to Redis → All clients in room receive broadcast
- Round reveal: Host sends `round.reveal.v1` → Server calculates stats → Publish `round.revealed.v1` with all votes → All clients receive
- Events broadcast via Redis Pub/Sub to support horizontal scaling across multiple application nodes
- Sub-100ms latency target for vote events and reveals

### Context: WebSocket Protocol Specification (from api/websocket-protocol.md)

The WebSocket protocol defines the message envelope format and lifecycle:

**Message Envelope Structure:**
```json
{
  "type": "message_type.v1",
  "requestId": "uuid",
  "payload": { /* message-specific data */ }
}
```

**Key Message Types for Testing:**
- `room.join.v1`: Client → Server (participant joins room)
- `vote.cast.v1`: Client → Server (cast vote, payload: `{"cardValue": "5"}`)
- `vote.recorded.v1`: Server → Client broadcast (vote confirmed, does NOT reveal value)
- `round.reveal.v1`: Client → Server (host triggers reveal, empty payload)
- `round.revealed.v1`: Server → Client broadcast (votes revealed with statistics)
- `round.reset.v1`: Client → Server (host resets round)
- `error.v1`: Server → Client (error response with code, message)

**Error Codes:**
- `4000`: UNAUTHORIZED (invalid JWT)
- `4001`: ROOM_NOT_FOUND
- `4002`: INVALID_VOTE
- `4003`: FORBIDDEN (insufficient permissions, e.g., non-host trying to reveal)
- `4005`: INVALID_STATE (action not valid in current room state)

**Connection Lifecycle:**
1. WebSocket handshake with JWT in query param: `?token={jwt}`
2. Client must send `room.join.v1` immediately after connection
3. Server subscribes to Redis channel `room:{roomId}`
4. Heartbeat protocol: ping every 30s, timeout after 60s
5. Graceful disconnect: send `room.leave.v1` before closing
6. Reconnection: include `lastEventId` for event replay within 5-minute window

### Context: Vote Casting Sequence Diagram (from 04_Behavior_and_Communication.md)

```
Alice (Client A) → WebSocket Handler A → Voting Service → Database
                                       ↓
                         Redis Pub/Sub (room:abc123)
                                       ↓
                    All WebSocket Handlers (A, B, C) → All Clients

Flow:
1. Alice sends vote.cast.v1 {"cardValue": "5"}
2. VotingService.castVote() persists to database
3. Publish vote.recorded.v1 to Redis (does NOT include card value)
4. All clients receive broadcast showing Alice voted (checkmark, no value)
5. Host sends round.reveal.v1
6. VotingService.revealRound() calculates statistics
7. Publish round.revealed.v1 with all votes and stats
8. All clients receive broadcast with vote values and statistics
```

**Critical for Testing:**
- Vote secrecy: `vote.recorded.v1` does NOT include card value
- Statistics calculation on reveal: average, median, consensus detection
- Multi-client synchronization via Redis Pub/Sub

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `src/main/java/com/terrencecurran/planningpoker/websocket/RoomWebSocket.java`
    *   **Summary:** This is the existing WebSocket endpoint implementation using Quarkus WebSockets Next (`@WebSocket(path = "/ws/room/{roomId}")`). It handles connection lifecycle (`@OnOpen`, `@OnClose`, `@OnError`), message routing (`@OnTextMessage` with switch on message type), and room state broadcasting via `broadcastRoomState()`.
    *   **Key Pattern:** Uses reactive `Uni<>` types throughout, with `@ApplicationScoped` for CDI injection.
    *   **Current Implementation:** Handles `JOIN_ROOM`, `VOTE`, `REVEAL_CARDS`, `HIDE_CARDS`, `RESET_VOTES`, `TOGGLE_OBSERVER` message types. Uses Jackson `ObjectMapper` for JSON serialization. Maintains connection registries in `ConcurrentHashMap` structures.
    *   **IMPORTANT NOTE:** The current implementation uses different message type names than the architecture spec. It uses `JOIN_ROOM` instead of `room.join.v1`, `VOTE` instead of `vote.cast.v1`, etc. Your tests should use the message types that the ACTUAL implementation expects, not necessarily what the spec says.

*   **File:** `src/main/java/com/terrencecurran/planningpoker/service/RoomService.java`
    *   **Summary:** Domain service handling room operations using Hibernate Reactive Panache. Contains methods: `createRoom()`, `joinRoom()`, `castVote()`, `revealCards()`, `hideCards()`, `resetVotes()`, `toggleObserver()`, `disconnectPlayer()`, `getRoomState()`.
    *   **Transaction Pattern:** All write operations wrapped in `Panache.withTransaction(() -> ...)`, read operations use `Panache.withSession(() -> ...)`.
    *   **Vote Handling:** `castVote()` finds existing vote by room + player + votingRound, updates if exists (upsert pattern). Returns `Uni<Vote>`.
    *   **Reveal Logic:** `revealCards()` sets `room.areCardsRevealed = true`. Statistics calculated in `buildRoomState()` when cards revealed: average, median, consensus (when all same), vote counts by value.
    *   **Consensus Detection:** `voteCountMap.size() == 1` indicates consensus (all votes identical).

*   **File:** `src/main/java/com/terrencecurran/planningpoker/entity/Room.java`
    *   **Summary:** JPA entity with fields: `id` (String UUID), `name`, `createdAt`, `isVotingActive` (boolean), `areCardsRevealed` (boolean), OneToMany relationships to `players` and `votes` (EAGER fetch).
    *   **Note:** The Room entity has EAGER fetching for relationships. Be mindful of N+1 query issues in tests.

### Existing Test Patterns

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** This is an EXCELLENT reference for Quarkus reactive integration testing patterns. Study this file carefully!
    *   **Key Patterns:**
        *   Uses `@QuarkusTest` annotation for full integration test with Testcontainers auto-configured
        *   Uses `@RunOnVertxContext` annotation on test methods for reactive execution
        *   Uses `UniAsserter` parameter in test methods for reactive assertions
        *   Transaction pattern: `asserter.execute(() -> Panache.withTransaction(() -> ...))` for setup
        *   Assertion pattern: `asserter.assertThat(() -> Panache.withTransaction(() -> repo.findById(id)), result -> { assertThat(result)... })`
        *   Helper methods for creating test entities: `createTestUser()`, `createTestRoom()`, `createTestRound()`, `createTestParticipant()`, `createTestVote()`
        *   Cleanup in `@BeforeEach`: Delete entities in reverse dependency order (children first, then parents)
    *   **CRITICAL:** You MUST follow the exact same patterns for reactive testing. Standard JUnit assertions won't work with reactive types.

### Implementation Tips & Notes

*   **Tip:** Quarkus provides `io.quarkus:quarkus-test-vertx` dependency which is already in `pom.xml` (line 107-109). This provides the WebSocket testing client capabilities you'll need.

*   **Tip:** For WebSocket client testing in Quarkus, you can use `WebSocketTestClient` or the Vert.x WebSocket client. Look for examples using `io.vertx.core.http.WebSocketConnectOptions` and `vertx.createHttpClient()`.

*   **Note:** The current codebase does NOT appear to have Redis Pub/Sub implemented yet. The task description mentions Redis, but the existing `RoomWebSocket.java` doesn't show any Redis integration. You may need to:
    1. First verify if Redis integration exists elsewhere in the codebase
    2. If not, your tests may need to mock Redis or test the WebSocket behavior without Redis initially
    3. Focus on testing the WebSocket message flow and room state synchronization first

*   **Warning:** The existing implementation in `RoomWebSocket.java` (line 30-50) uses a simple in-memory `ConcurrentHashMap` for connection tracking. This means multi-node testing (with Redis Pub/Sub) may require additional infrastructure setup beyond just Testcontainers.

*   **Note:** For Testcontainers setup, you'll need:
    - PostgreSQL container (already used in repository tests)
    - Redis container (if testing Redis Pub/Sub functionality)
    - Configure Quarkus to use these containers via test application properties

*   **Testing Strategy Recommendation:**
    1. **Phase 1:** Test single-client WebSocket flow without Redis (connect, join, vote, reveal, reset)
    2. **Phase 2:** Test multi-client synchronization within same application instance (simpler than cross-node)
    3. **Phase 3:** Test authorization (non-host reveal attempt)
    4. **Phase 4:** Test reconnection (if time permits)

*   **Message Type Translation:** The actual implementation uses simple string types like `"VOTE"`, `"REVEAL_CARDS"`, etc. (see line 82-94 in RoomWebSocket.java). Your test messages should match this, not the versioned format from the protocol spec (`vote.cast.v1`).

*   **JSON Message Format:** Study the message classes in `src/main/java/com/terrencecurran/planningpoker/websocket/message/` directory. These define the actual message structure used by the current implementation:
    - `BaseMessage.java` - has `type` field
    - `VoteMessage.java` - has `value` field (not `cardValue`)
    - `JoinRoomMessage.java` - has `username` field
    - Look at `RoomStateMessage.java` for the state snapshot structure

*   **CRITICAL:** Before starting, run `mvn clean verify` to ensure the existing codebase compiles and passes all tests. This establishes your baseline.

*   **Assertion Library:** The project uses AssertJ (line 117-119 in pom.xml). Use AssertJ's fluent assertions: `assertThat(result).isNotNull().hasSize(3)...`

*   **WebSocket Testing Example Pattern:**
```java
@QuarkusTest
class VotingFlowIntegrationTest {

    @Inject
    Vertx vertx;

    @Test
    @RunOnVertxContext
    void testVoteCastingFlow(UniAsserter asserter) {
        // Given: WebSocket connection
        asserter.execute(() -> {
            // Connect to WebSocket
            HttpClient client = vertx.createHttpClient();
            WebSocketConnectOptions options = new WebSocketConnectOptions()
                .setURI("/ws/room/test123")
                .setHost("localhost")
                .setPort(8081);

            return client.webSocket(options)
                .onItem().transformToUni(ws -> {
                    // Send join message
                    // Send vote message
                    // Assert responses
                    return Uni.createFrom().voidItem();
                });
        });
    }
}
```

*   **Docker Compose Check:** The project has `docker-compose.yml` which likely includes PostgreSQL and Redis services. You may be able to leverage this for local testing, though Testcontainers is preferred for CI/CD.

### Project Structure Notes

*   **Test Source Directory:** Tests go in `backend/src/test/java` (note: the actual project root has a separate `src/` directory at top level, but Java backend is under `backend/` subdirectory, as configured in pom.xml lines 124-135)
*   **Package Structure:** Use package `com.scrumpoker.api.websocket` for the test (matching the target file path in task spec)
*   **Test Naming:** Follow the existing pattern: `*Test.java` for unit tests, `*IntegrationTest.java` for integration tests
*   **Current State:** The test directory structure exists (`backend/src/test/java/com/scrumpoker/repository/`) with 10+ repository integration tests, but NO WebSocket tests yet. You'll be creating the first WebSocket integration test.

### Key Decision: Testing Without Full Redis Implementation

**IMPORTANT DISCOVERY:** The current `RoomWebSocket.java` implementation does NOT have Redis Pub/Sub integration yet. The `broadcastRoomState()` method (line 251-307) directly broadcasts to all locally connected clients stored in the `connectionToRoom` map, but there's no Redis publish/subscribe happening.

**Your Testing Strategy Should Be:**

1. **Focus on Single-Instance WebSocket Flow First:** Test that the WebSocket message handling, room state synchronization, and vote lifecycle work correctly within a single application instance. This is still valuable integration testing and validates the core voting flow.

2. **Document the Redis Gap:** In your test comments, note that Redis Pub/Sub integration is not yet implemented in the codebase. Your tests validate the WebSocket protocol and voting logic, but do not test horizontal scaling across multiple application nodes.

3. **Test What Exists:** The acceptance criteria say "Vote cast by client A received by client B via Redis Pub/Sub", but since Redis isn't implemented yet, your tests should focus on "Vote cast by client A received by client B via the WebSocket broadcast mechanism". This still validates the event synchronization pattern.

4. **Future-Proof the Tests:** Structure your tests so that when Redis is eventually integrated, minimal changes will be needed. Use the correct message types from the protocol spec, test with multiple clients, and verify event ordering.

---

**End of Task Briefing Package**

Good luck with the implementation! Focus on getting a basic WebSocket connection test working first, then build up to the full voting flow. Remember to follow the existing reactive testing patterns exactly as shown in `VoteRepositoryTest.java`.
