# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

**Task I4.T7**: Create integration tests for complete voting flow using Quarkus test WebSocket client. Test scenarios:
1. Connect to room, cast vote, receive vote.recorded event, host reveals round, receive round.revealed event with statistics, reset round, votes cleared
2. Multi-client scenario (2+ clients in same room, votes synchronize via Redis Pub/Sub)
3. Authorization (non-host cannot reveal)
4. Disconnect/reconnect (client disconnects, reconnects, state restored)

Use Testcontainers for Redis and PostgreSQL.

**Acceptance Criteria**:
- `mvn verify` runs WebSocket integration tests successfully
- Vote cast by client A received by client B via Redis Pub/Sub
- Reveal calculates correct statistics (known vote inputs)
- Non-host reveal attempt returns error message
- Reconnection test joins room and receives current state
- All tests pass with Testcontainers

---

## Issues Detected

*   **WebSocket Client Context Issue:** All 4 VotingFlowIntegrationTest tests are failing with `ContextNotActiveException: RequestScoped context was not active when trying to obtain a bean instance for a client proxy of CLASS bean [class=io.quarkus.security.runtime.SecurityIdentityAssociation]`

*   **Root Cause Analysis:**
    - The WebSocket test client (`WebSocketTestClient.java`) is trying to connect to the server from within a test execution context
    - Quarkus's `ContainerProvider.getWebSocketContainer()` returns the server's WebSocket container, which has security enabled and requires an active `RequestScoped` context
    - When the client tries to establish a connection, it attempts to access `SecurityIdentityAssociation` bean, which is `@RequestScoped`
    - Since the WebSocket client is being initialized outside of a request context, this fails
    - Attempted fix using Jetty's standalone client (`JakartaWebSocketClientContainerProvider.getWebSocketContainer()`) did not work - it still delegates to Quarkus's server container

*   **Configuration Issues Fixed:**
    - ✅ Redis Dev Services configuration - Fixed by adding `%prod` and `%dev` prefixes to `quarkus.redis.hosts` in main `application.properties` so tests use Dev Services
    - ✅ Database constraint violations in `AuditLogRepositoryTest` and `OrgMemberRepositoryTest` - Fixed by adding `RoomParticipantRepository` cleanup before deleting users

---

## Best Approach to Fix

The WebSocket client security context issue requires one of the following solutions:

### Option 1: Use Jetty Standalone Client Directly (Recommended)
Modify `WebSocketTestClient.java` to bypass Quarkus's container provider entirely by directly instantiating Jetty's internal WebSocket client:

```java
import org.eclipse.jetty.websocket.jakarta.client.internal.JakartaWebSocketClientContainer;

public class WebSocketTestClient {
    private static JakartaWebSocketClientContainer jettyClient;

    static {
        try {
            jettyClient = new JakartaWebSocketClientContainer();
            jettyClient.start();
        } catch (Exception e) {
            throw new RuntimeException("Failed to start Jetty WebSocket client", e);
        }
    }

    public void connect(String uri) throws Exception {
        session = jettyClient.connectToServer(this, URI.create(uri));
    }
}
```

### Option 2: Disable Security for WebSocket Tests
Add to `src/test/resources/application.properties`:
```properties
quarkus.security.enabled=false
quarkus.oidc.enabled=false
quarkus.http.auth.proactive=false
```

### Option 3: Activate Request Context in Tests
Wrap the WebSocket connection code in `@ActivateRequestContext` or manually activate the request context:
```java
@ActivateRequestContext
public void connect(String uri) throws Exception {
    // connection code
}
```

**Recommended Action:** Try Option 1 first (use Jetty internal client directly). If that doesn't work due to classpath issues, try Option 2 (disable security entirely for tests since auth is already bypassed via `permit-all` policy).

---

## Additional Notes

- All other test failures have been resolved:
  - Redis Dev Services now properly starts via Testcontainers
  - Database cleanup order fixed to prevent foreign key constraint violations
- The VotingFlowIntegrationTest code itself is correct and complete
- Tests just need the WebSocket client context issue resolved to pass
