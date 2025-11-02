# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration tests for complete voting flow using Quarkus test WebSocket client. Test scenarios: connect to room, cast vote, receive vote.recorded event, host reveals round, receive round.revealed event with statistics, reset round, votes cleared. Test multi-client scenario (2+ clients in same room, votes synchronize). Test authorization (non-host cannot reveal). Test disconnect/reconnect (client disconnects, reconnects, state restored). Use Testcontainers for Redis and PostgreSQL.

**Acceptance Criteria:**
- `mvn verify` runs WebSocket integration tests successfully
- Vote cast by client A received by client B via Redis Pub/Sub
- Reveal calculates correct statistics (known vote inputs)
- Non-host reveal attempt returns error message
- Reconnection test joins room and receives current state
- All tests pass with Testcontainers

---

## Issues Detected

*   **Critical Runtime Error:** The WebSocket message handler `onMessage()` in `RoomWebSocketHandler.java` is being called from Undertow's thread pool, NOT the Vert.x event loop. When the `MessageRouter` routes messages to handlers like `VoteCastHandler`, `RoundRevealHandler`, and `RoundResetHandler`, those handlers attempt to use Hibernate Reactive operations (`@WithTransaction`), which requires a Vert.x context. This causes the error: `java.lang.IllegalStateException: No current Vertx context found at io.quarkus.hibernate.reactive.panache.common.runtime.SessionOperations.vertxContext`.

*   **Test Failure:** All integration tests in `VotingFlowIntegrationTest.java` are failing because the WebSocket handlers crash when processing messages. The test assertion `assertThat(voteRecorded).isNotNull()` at line 132 fails with `AssertionError: Expecting actual not to be null` because the `vote.recorded.v1` event is never published due to the handler crashing.

*   **Root Cause:** The `@OnMessage` annotation alone does not ensure the method runs on the Vert.x event loop. The Undertow WebSocket implementation calls this method from its own thread pool. Since the git diff shows that `@WithTransaction` was recently added to the message handlers (`VoteCastHandler.java:48`, `RoundRevealHandler.java:46`, `RoundResetHandler.java:46`), this introduced a dependency on Vert.x context that didn't exist before. However, the `RoomWebSocketHandler.onMessage()` method was not updated to ensure it runs on the Vert.x event loop.

---

## Best Approach to Fix

You MUST modify `RoomWebSocketHandler.java` to ensure the `onMessage()` method dispatches all WebSocket message handling to the Vert.x event loop. The fix requires wrapping the message routing logic inside `vertx.executeBlocking()` or using Vert.x context dispatch.

### Recommended Solution

In `RoomWebSocketHandler.java`, modify the `onMessage()` method (starting around line 232) to dispatch the message routing logic to the Vert.x event loop:

**Current broken code (line 270):**
```java
// Route to message handlers via MessageRouter
// Fire and forget - the router handles its own context management
messageRouter.route(session, message, userId, roomId)
        .subscribe().with(
                success -> Log.debugf("Message handled: type=%s, requestId=%s",
                        message.getType(), message.getRequestId()),
                failure -> Log.errorf(failure, "Failed to handle message: type=%s, requestId=%s",
                        message.getType(), message.getRequestId())
        );
```

**Fixed code:**
```java
// Route to message handlers via MessageRouter
// CRITICAL: Dispatch to Vert.x event loop for Hibernate Reactive operations
io.vertx.core.Context context = vertx.getOrCreateContext();
WebSocketMessage finalMessage = message;  // Capture for lambda
context.runOnContext(v -> {
    messageRouter.route(session, finalMessage, userId, roomId)
            .subscribe().with(
                    success -> Log.debugf("Message handled: type=%s, requestId=%s",
                            finalMessage.getType(), finalMessage.getRequestId()),
                    failure -> Log.errorf(failure, "Failed to handle message: type=%s, requestId=%s",
                            finalMessage.getType(), finalMessage.getRequestId())
            );
});
```

### Additional Notes

1. The `vertx` field was already injected in `RoomWebSocketHandler.java` (line 94-95 in the git diff), so you can use it directly.

2. You MUST use `context.runOnContext()` to ensure the Mutiny `Uni` chain returned by `messageRouter.route()` executes on the Vert.x event loop, where Hibernate Reactive can access the session context.

3. You need to create a final reference to `message` for use inside the lambda (Java lambda capture requirement).

4. The `@WithTransaction` annotations added to the message handlers (`VoteCastHandler`, `RoundRevealHandler`, `RoundResetHandler`) are correct and should remain unchanged. The issue is NOT in those handlers - it's in the WebSocket entry point.

5. After this fix, all integration tests should pass because:
   - Vote casting will succeed (Hibernate Reactive operations will work)
   - The `VotingService` will publish `vote.recorded.v1` events to Redis
   - The `RoomEventSubscriber` will broadcast events to all WebSocket clients
   - The test assertions for `voteRecorded.isNotNull()` will succeed

### Verification

After making this change, run `mvn verify -Dtest=VotingFlowIntegrationTest` to verify all tests pass. You should see:
- `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`
- No more "No current Vertx context found" errors in the logs
