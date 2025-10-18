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

### Context: Integration Testing Strategy (from 03_Verification_and_Glossary.md)

```markdown
#### Integration Testing

**Scope:** Multiple components working together with real infrastructure (database, cache, message queue)

**Framework:** Quarkus Test (`@QuarkusTest`), Testcontainers, REST Assured

**Coverage Target:** Critical integration points (API → Service → Repository → Database)

**Approach:**
- Use Testcontainers for PostgreSQL and Redis (real instances, not mocks)
- Test REST endpoints end-to-end (request → response with database persistence)
- Test WebSocket flows (connection → message handling → database → Pub/Sub broadcast)
- Verify transaction boundaries and data consistency
- Run in CI pipeline (longer execution time acceptable: 10-15 minutes)

**Examples:**
- `RoomControllerTest`: POST /rooms creates database record, GET retrieves it
- `VotingFlowIntegrationTest`: WebSocket vote message → database insert → Redis Pub/Sub → client broadcast
- `StripeWebhookControllerTest`: Webhook event → signature verification → database update

**Acceptance Criteria:**
- All integration tests pass (`mvn verify`)
- Testcontainers start successfully (PostgreSQL, Redis)
- Database schema migrations execute correctly in tests
- No test pollution (each test isolated with database cleanup)
```

### Context: WebSocket Integration Testing (from 03_Verification_and_Glossary.md)

```markdown
3. **WebSocket Integration:**
   - WebSocket handlers depend on services and event publisher
   - Integration tests verify WebSocket message → handler → service → database → Redis Pub/Sub → broadcast
   - Use Quarkus WebSocket test client or custom WebSocket client library
```

### Context: WebSocket Protocol - Vote Casting (from websocket-protocol.md)

```markdown
#### 4.1.3 `vote.cast.v1`

**Purpose:** Participant casts vote for the current round.

**Payload Schema:**
```json
{
  "cardValue": "5"                    // Required, 1-10 characters, must match current deck
}
```

**Valid Card Values (depends on deck type):**
- Fibonacci: `"0"`, `"1"`, `"2"`, `"3"`, `"5"`, `"8"`, `"13"`, `"21"`, `"?"`
- T-Shirt: `"XS"`, `"S"`, `"M"`, `"L"`, `"XL"`, `"XXL"`, `"?"`
- Powers of 2: `"1"`, `"2"`, `"4"`, `"8"`, `"16"`, `"32"`, `"?"`

**Server Broadcast:**
- `vote.recorded.v1` to all participants (does NOT include vote value)

**Error Conditions:**
- `4002`: Invalid vote (card value not in deck, no active round, already voted)
- `4003`: Forbidden (observer role cannot vote)
```

### Context: Round Reveal Message (from websocket-protocol.md)

```markdown
#### 4.2.7 `round.revealed.v1` (Broadcast)

**Purpose:** Broadcast when votes are revealed with statistics.

**Payload Schema:**
```json
{
  "roundId": "550e8400-e29b-41d4-a716-446655440000",
  "revealedAt": "2025-10-17T10:22:00Z",
  "votes": [
    {
      "participantId": "user-123",
      "displayName": "Alice Smith",
      "cardValue": "5",
      "votedAt": "2025-10-17T10:21:30Z"
    }
  ],
  "statistics": {
    "average": 6.5,
    "median": 6.5,
    "mode": "5",
    "consensusReached": false,
    "totalVotes": 2,
    "distribution": {
      "5": 1,
      "8": 1
    }
  }
}
```

**Statistics Fields:**
- `average`: Mean of numeric votes (null if no numeric votes)
- `median`: Median of numeric votes (null if no numeric votes)
- `consensusReached`: Boolean indicating if variance is below threshold
- `totalVotes`: Count of votes in round
```

### Context: Error Code Catalog (from websocket-protocol.md)

```markdown
| Code | Error | Description |
|------|-------|-------------|
| **4002** | `INVALID_VOTE` | Vote validation failed (invalid card value, no active round) |
| **4003** | `FORBIDDEN` | Insufficient permissions (e.g., observer trying to vote, non-host starting round) |
| **4005** | `INVALID_STATE` | Action not valid in current room/round state |
| **4999** | `INTERNAL_SERVER_ERROR` | Unexpected server error |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/api/websocket/VotingFlowIntegrationTest.java`
    *   **Summary:** **THIS FILE ALREADY EXISTS** with complete implementation of all required test scenarios. It contains 4 test methods covering the complete voting flow, multi-client synchronization via Redis Pub/Sub, authorization failures, and reconnection.
    *   **Current Implementation Status:**
        1. ✅ `testCompleteVotingFlow_CastRevealReset()` - Complete vote → reveal → reset flow with statistics verification
        2. ✅ `testMultipleClientsReceiveSynchronizedEvents()` - Redis Pub/Sub synchronization across clients
        3. ✅ `testNonHostCannotRevealRound_ReturnsForbidden()` - Authorization enforcement (code 4003)
        4. ✅ `testReconnectionPreservesRoomState()` - Reconnection and vote persistence
    *   **Test Implementation Details:**
        - Uses `@QuarkusTest` with `@RunOnVertxContext` for reactive testing
        - Uses `UniAsserter` pattern for async assertions
        - Properly cleans up test data in `@BeforeEach` (votes → rounds → participants → rooms → users)
        - Generates JWT tokens using `JwtTokenService`
        - Uses `WebSocketTestClient` helper for WebSocket connections
        - Tests run against `ws://localhost:8081/ws/room/{roomId}?token={jwt}`
        - Verifies statistics: average=6.5, median="6.5", consensus=false for votes "5" and "8"
    *   **Recommendation:** **THE TASK IS ALREADY COMPLETE.** All deliverables and acceptance criteria are met. You should verify tests pass with `mvn verify` and mark the task as done.

*   **File:** `backend/src/test/java/com/scrumpoker/api/websocket/WebSocketTestClient.java`
    *   **Summary:** Helper class implementing `@ClientEndpoint` for WebSocket integration testing. Provides methods for connection management, message sending/receiving, and timeout-based message waiting.
    *   **Key Methods:**
        - `connect(String uri)` - Connects to WebSocket endpoint with JWT token
        - `send(String type, Map<String, Object> payload)` - Sends message with auto-generated requestId
        - `awaitMessage(String messageType, Duration timeout)` - Waits for specific message type with timeout
        - `payload(Object... keyValues)` - Helper to create payload maps easily
    *   **Implementation Details:**
        - Uses `BlockingQueue<WebSocketMessage>` for thread-safe message handling
        - `ObjectMapper` for JSON serialization/deserialization
        - Filters messages by type in `awaitMessage()` and re-queues non-matching messages
        - Handles `@OnOpen`, `@OnMessage`, `@OnClose`, `@OnError` WebSocket lifecycle events
    *   **Recommendation:** This utility is fully implemented and working. It's used by all tests in VotingFlowIntegrationTest.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** Domain service implementing reactive voting operations. All methods return `Uni<>` and use `@WithTransaction`.
    *   **Key Methods:**
        - `castVote(roomId, roundId, participantId, cardValue)` - Upsert vote (update if exists)
        - `startRound(roomId, storyTitle)` - Creates Round with next round number
        - `revealRound(roomId, roundId)` - Calculates statistics using ConsensusCalculator
        - `resetRound(roomId, roundId)` - Deletes votes and resets Round statistics
    *   **Event Publishing:** Each operation publishes events to Redis Pub/Sub via `RoomEventPublisher`
        - `vote.recorded.v1` with `{participantId, votedAt}`
        - `round.revealed.v1` with `{votes[], stats{avg, median, consensus}}`
        - `round.reset.v1` with `{roundId}`
    *   **Recommendation:** VotingService is the core business logic. Tests verify that its event publishing integrates correctly with WebSocket broadcasting.

*   **File:** `backend/src/main/java/com/scrumpoker/api/websocket/handler/VoteCastHandler.java`
    *   **Summary:** WebSocket message handler for `vote.cast.v1`. Validates payload, checks permissions, delegates to VotingService.
    *   **Validation Chain:**
        1. Extract and validate `cardValue` from payload
        2. Find latest active round (not yet revealed)
        3. Find participant by userId and roomId
        4. Check role is not OBSERVER (observers cannot vote)
        5. Call `VotingService.castVote()`
    *   **Error Codes:**
        - 4004: `VALIDATION_ERROR` - Missing or invalid cardValue
        - 4005: `INVALID_STATE` - No active round or round already revealed
        - 4003: `FORBIDDEN` - Participant not found or observer trying to vote
        - 4999: `INTERNAL_SERVER_ERROR` - Unexpected failure
    *   **Recommendation:** The tests verify this handler's authorization logic works correctly, especially the observer role check and active round validation.

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Test configuration using Quarkus Dev Services (Testcontainers) for automatic PostgreSQL and Redis provisioning.
    *   **Key Settings:**
        - No explicit JDBC/reactive URLs → Testcontainers auto-configures databases
        - `quarkus.oidc.enabled=false` → Disables OAuth for tests
        - `quarkus.http.auth.permission.permit-all.paths=/*` → Bypasses security checks
        - `quarkus.flyway.migrate-at-start=true` → Runs migrations on startup
    *   **Recommendation:** Configuration is correct. Testcontainers will automatically start PostgreSQL and Redis when tests run.

### Implementation Tips & Notes

*   **CRITICAL:** **THE TASK I4.T7 IS ALREADY COMPLETE.** The file `VotingFlowIntegrationTest.java` exists with full implementation of all 4 required test scenarios. All deliverables and acceptance criteria are met:
    - ✅ Complete vote → reveal → reset flow test
    - ✅ Multi-client synchronization test (Redis Pub/Sub)
    - ✅ Authorization failure test (non-host cannot reveal, error 4003)
    - ✅ Reconnection preserves room state test
    - ✅ Testcontainers setup for PostgreSQL and Redis
    - ✅ Proper test cleanup and isolation

*   **Note:** The tests use `@QuarkusTest` which automatically starts Testcontainers via Quarkus Dev Services. The test configuration explicitly avoids setting database URLs to trigger Testcontainers auto-provisioning.

*   **Note:** The test pattern uses `@RunOnVertxContext` with `UniAsserter` for reactive testing. Database operations are wrapped in `Panache.withTransaction()` and chained using `.chain()` for proper sequencing.

*   **Important:** JWT tokens are generated synchronously within the `UniAsserter.execute()` block by storing them in an array, ensuring tokens are available before WebSocket tests run. This prevents race conditions.

*   **Important:** Test data cleanup in `@BeforeEach` deletes entities in the correct order (children first: votes → rounds → participants → rooms → users) to avoid foreign key constraint violations.

*   **Tip:** The tests verify Redis Pub/Sub by checking that when Alice casts a vote, Bob receives the `vote.recorded.v1` event. This confirms multi-node event broadcasting works correctly.

*   **Tip:** The reveal test uses known vote values ("5" and "8") to verify statistics calculation: average=6.5, median="6.5", consensus=false. This validates the ConsensusCalculator logic.

*   **Tip:** The authorization test verifies that a VOTER attempting to reveal the round receives `error.v1` with code 4003 and error "FORBIDDEN", confirming role-based access control.

*   **Verification Steps:**
    1. Run `mvn verify` from the backend directory
    2. Verify all tests in `VotingFlowIntegrationTest` pass
    3. Confirm Testcontainers starts PostgreSQL and Redis
    4. Check test logs show event broadcasting via Redis Pub/Sub

*   **Final Recommendation:** Since all test scenarios are already implemented and working, your task is to:
    1. **Verify the tests pass:** Run `mvn verify` and confirm all 4 test methods pass
    2. **Review the implementation:** Ensure it meets all acceptance criteria (it does)
    3. **Mark the task as done:** Update the task manifest to set `"done": true` for I4.T7

---

## CRITICAL FINDING: TASK ALREADY COMPLETE

**The task I4.T7 has been fully implemented.** The file `backend/src/test/java/com/scrumpoker/api/websocket/VotingFlowIntegrationTest.java` exists with complete test coverage for all required scenarios:

1. ✅ **Complete Voting Flow:** `testCompleteVotingFlow_CastRevealReset()` - Tests vote casting, reveal with statistics, and round reset
2. ✅ **Multi-Client Sync:** `testMultipleClientsReceiveSynchronizedEvents()` - Tests Redis Pub/Sub event broadcasting
3. ✅ **Authorization:** `testNonHostCannotRevealRound_ReturnsForbidden()` - Tests non-host receives error 4003 (FORBIDDEN)
4. ✅ **Reconnection:** `testReconnectionPreservesRoomState()` - Tests disconnect/reconnect preserves votes

**All acceptance criteria met:**
- ✅ Uses `@QuarkusTest` with Testcontainers for PostgreSQL and Redis
- ✅ Verifies Redis Pub/Sub synchronization across WebSocket clients
- ✅ Verifies statistics calculation (avg=6.5, median="6.5", consensus=false)
- ✅ Verifies authorization enforcement (non-host cannot reveal)
- ✅ Verifies reconnection behavior

**Action Required:** Run `mvn verify` to confirm tests pass, then mark task I4.T7 as complete.
