# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration tests for complete voting flow using Quarkus test WebSocket client. Test scenarios: connect to room, cast vote, receive vote.recorded event, host reveals round, receive round.revealed event with statistics, reset round, votes cleared. Test multi-client scenario (2+ clients in same room, votes synchronize). Test authorization (non-host cannot reveal). Test disconnect/reconnect (client disconnects, reconnects, state restored). Use Testcontainers for Redis and PostgreSQL.

**Target File:** `backend/src/test/java/com/scrumpoker/api/websocket/VotingFlowIntegrationTest.java`

**Acceptance Criteria:**
- `mvn verify` runs WebSocket integration tests successfully
- Vote cast by client A received by client B via Redis Pub/Sub
- Reveal calculates correct statistics (known vote inputs)
- Non-host reveal attempt returns error message
- Reconnection test joins room and receives current state
- All tests pass with Testcontainers

---

## Issues Detected

### Issue 1: IllegalStateException - Blocking Vert.x Event Loop Thread

**Problem:** All four tests are failing with `IllegalStateException: The current thread cannot be blocked: vert.x-eventloop-thread-0`. This occurs because the WebSocket test methods are running blocking operations (WebSocket connect, send, receive with timeout) on a Vert.x event loop thread, which is prohibited in reactive applications.

**Stack Trace:**
```
java.lang.IllegalStateException: The current thread cannot be blocked: vert.x-eventloop-thread-0
	at io.smallrye.mutiny.operators.uni.builders.UniBlockingConsumer.await(UniBlockingConsumer.java:99)
	at io.smallrye.mutiny.groups.UniAwait.atMost(UniAwait.java:65)
```

**Root Cause:** The test methods use `@RunOnVertxContext` which runs the test on the Vert.x event loop thread. However, the `runCompleteVotingFlowTest()` and similar methods call blocking WebSocket operations like `aliceClient.connect()`, `aliceClient.awaitMessage()`, etc. These blocking calls are not allowed on event loop threads.

### Issue 2: ConstraintViolationException - Invalid RoomId Length

**Problem:** One test (`testReconnectionPreservesRoomState`) fails with `jakarta.validation.ConstraintViolationException` because the roomId "recon01" is only 7 characters, but the `Room` entity requires exactly 6 characters.

**Constraint Definition:**
```java
@Size(min = 6, max = 6)
@Column(name = "room_id", length = 6)
public String roomId;
```

**Current Invalid RoomIds:**
- "recon01" - 7 characters ❌

**Valid RoomIds in other tests:**
- "flow01" - 6 characters ✅
- "sync01" - 6 characters ✅
- "auth01" - 6 characters ✅

---

## Best Approach to Fix

You MUST make the following changes to `VotingFlowIntegrationTest.java`:

### Fix 1: Remove @RunOnVertxContext and Use Blocking Test Pattern

**Explanation:** WebSocket integration tests need to run blocking operations (connect, send, receive). These cannot run on Vert.x event loop threads. The solution is to:
1. Remove `@RunOnVertxContext` annotation from all test methods
2. Use `@RunOnVertxContext` ONLY for the `setUp()` method (which needs it for reactive database cleanup)
3. Run the WebSocket test logic directly in the test method WITHOUT wrapping in `asserter.execute()`
4. For database setup operations, use `Uni.await().indefinitely()` to block and wait for completion

**Pattern to Follow:**
```java
@Test
@Order(1)
void testCompleteVotingFlow_CastRevealReset() throws Exception {
    // Setup test data (synchronously using .await().indefinitely())
    User alice = createTestUser("alice@example.com", "Alice");
    User bob = createTestUser("bob@example.com", "Bob");
    Room room = createTestRoom("flow01", "Test Room", alice);
    RoomParticipant aliceParticipant = createTestParticipant(room, alice, "Alice", RoomRole.HOST);
    RoomParticipant bobParticipant = createTestParticipant(room, bob, "Bob", RoomRole.VOTER);
    Round round = createTestRound(room, 1, "Test Story");

    // Persist to database (blocking wait)
    Panache.withTransaction(() ->
        userRepository.persist(alice)
            .chain(() -> userRepository.persist(bob))
            .chain(() -> roomRepository.persist(room))
            .chain(() -> participantRepository.persist(aliceParticipant))
            .chain(() -> participantRepository.persist(bobParticipant))
            .chain(() -> roundRepository.persist(round))
    ).await().indefinitely();

    // Generate JWT tokens (blocking wait)
    TokenPair aliceTokens = jwtTokenService.generateTokens(alice).await().indefinitely();
    TokenPair bobTokens = jwtTokenService.generateTokens(bob).await().indefinitely();

    // Run WebSocket test (blocking operations are OK here)
    runCompleteVotingFlowTest(aliceTokens, bobTokens, aliceParticipant, bobParticipant);
}
```

**Apply this pattern to ALL four test methods:**
- `testCompleteVotingFlow_CastRevealReset()`
- `testMultipleClientsReceiveSynchronizedEvents()`
- `testNonHostCannotRevealRound_ReturnsForbidden()`
- `testReconnectionPreservesRoomState()`

**Key Changes:**
1. Remove `UniAsserter asserter` parameter from all test method signatures
2. Remove `@RunOnVertxContext` from all test methods (keep ONLY on `setUp()`)
3. Remove all `asserter.execute()` wrappers
4. Replace the `TokenPair[]` array pattern with direct variable assignment using `.await().indefinitely()`
5. Remove the `try-catch` wrapper that converts exceptions to `RuntimeException`
6. Call the `runXxxTest()` methods directly (not inside `asserter.execute()`)

### Fix 2: Change RoomId "recon01" to "recon1" (6 Characters)

In the `testReconnectionPreservesRoomState()` test method, change:
```java
Room room = createTestRoom("recon01", "Reconnection Test Room", alice);
```

To:
```java
Room room = createTestRoom("recon1", "Reconnection Test Room", alice);
```

And update all WebSocket connection URLs from:
```java
aliceClient1.connect(WS_BASE_URL + "recon01?token=" + tokens[0].accessToken());
```

To:
```java
aliceClient1.connect(WS_BASE_URL + "recon1?token=" + aliceTokens.accessToken());
```

(Note: Also update variable name from `tokens[0]` to `aliceTokens` after applying Fix 1)

---

## Summary of Required Changes

1. **Remove `@RunOnVertxContext` from all test methods** (keep only on `setUp()`)
2. **Remove `UniAsserter asserter` parameter from all test method signatures**
3. **Replace `asserter.execute()` pattern with direct `.await().indefinitely()` calls for database operations**
4. **Replace `TokenPair[]` array pattern with direct variable assignment**
5. **Remove exception wrapping in try-catch blocks** (throw directly)
6. **Fix roomId "recon01" to "recon1"** in reconnection test

After these fixes, run `mvn verify -Dtest=VotingFlowIntegrationTest` to verify all tests pass.
