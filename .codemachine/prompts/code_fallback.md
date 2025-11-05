# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration test for SSO authentication flow using mock IdP. Test OIDC: mock authorization server, valid ID token, callback processes successfully, user created (JIT provisioning), org assignment, JWT tokens returned. Test SAML2: mock SAML response, assertion validated, user provisioned, tokens returned. Test audit log entry creation. Use Testcontainers for PostgreSQL.

**Acceptance Criteria:**
- `mvn verify` runs SSO integration tests
- OIDC test creates user on first login
- User assigned to organization based on email domain
- SAML2 test works similarly
- JWT tokens returned contain org membership claim
- Audit log entry created for SSO login

---

## Issues Detected

*   **Test Failure:** The tests `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg` and `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership` are failing with "SocketTimeout Read timed out" errors at lines 147 and 255.
*   **Root Cause Analysis:** The tests are timing out during REST Assured HTTP calls. Despite having `asserter.execute()` wrappers, the HTTP calls are blocking on the Vert.x event loop. This can happen when:
    1. The endpoint handler is not properly processing the request (deadlock/hang)
    2. The Mockito mock is not set up correctly, causing the `SsoAdapter.authenticate()` call to hang
    3. The reactive chain in `AuthController.handleSsoCallback()` is not completing properly
*   **Additional Observations:**
    - 4 out of 6 tests are passing (the error tests that don't require full flow execution)
    - The 2 failing tests are both "success path" tests that invoke the full SSO callback flow
    - This suggests the issue is in the successful authentication path, not the REST Assured test setup

---

## Best Approach to Fix

**Step 1: Verify Mock Setup**

The Mockito mock setup in lines 108-119 needs to be verified. The current setup uses:

```java
when(ssoAdapter.authenticate(anyString(), anyString(), any(), any()))
    .thenAnswer(invocation -> {
        UUID orgId = invocation.getArgument(3);
        SsoUserInfo userInfo = new SsoUserInfo(...);
        return Uni.createFrom().item(userInfo);
    });
```

This should work, but ensure that:
1. The `@InjectMock` annotation is correctly replacing the `SsoAdapter` bean
2. The mock is being called with the correct parameter types
3. The returned `Uni` is not causing any threading issues

**Step 2: Add Debugging/Logging**

Temporarily add logging to diagnose where the hang is occurring:
- Add log statement at the start of the `@BeforeEach` method after mock setup
- Add log statement in the test method before and after the REST call
- Check if the test is hanging during setup, during HTTP call, or during assertion

**Step 3: Consider Alternative Approaches**

If the `@InjectMock` approach is not working reliably:
1. **Option A:** Switch to `@Alternative` bean approach (like the context document mentions using `MockSsoAdapter` class marked as `@Alternative`)
2. **Option B:** Use `@TestProfile` to inject a mock implementation
3. **Option C:** Increase the timeout for the HTTP call in RestAssured configuration

**Step 4: Verify Reactive Chain**

Review `backend/src/main/java/com/scrumpoker/api/rest/AuthController.java` lines 370-558 to ensure:
- All reactive operations use `.onItem()`, `.flatMap()`, or `.chain()` properly
- No blocking operations are on the event loop
- The error handling with `.onFailure().recoverWithItem()` is not causing issues
- The fire-and-forget audit log call is not blocking

**Step 5: Check Test Profile Configuration**

Verify that `SsoTestProfile` (lines 78-80) is correctly disabling security filters and not interfering with the mock injection.

---

## Recommended Action

**Priority 1:** Add timeout configuration to RestAssured calls:

```java
given()
    .contentType(ContentType.JSON)
    .body(request)
    .header("X-Forwarded-For", "192.168.1.100")
    .header("User-Agent", "Mozilla/5.0 Test Browser")
    .config(RestAssured.config().httpClient(
        HttpClientConfig.httpClientConfig()
            .setParam(CoreConnectionPNames.CONNECTION_TIMEOUT, 5000)
            .setParam(CoreConnectionPNames.SO_TIMEOUT, 30000)
    ))
.when()
    .post("/api/v1/auth/sso/callback")
```

**Priority 2:** Verify the Mockito mock is being called:

Add `verify(ssoAdapter, times(1)).authenticate(anyString(), anyString(), any(), any());` after the REST call to confirm the mock is invoked.

**Priority 3:** If still failing, switch to `@Alternative` bean approach:

Replace `@InjectMock SsoAdapter ssoAdapter;` with a test-scoped alternative implementation that doesn't use Mockito, following the pattern suggested in the context document.

---

## Expected Outcome

After implementing these fixes:
- All 6 tests should pass
- `mvn verify -Dtest=SsoAuthenticationIntegrationTest` should complete successfully
- The two previously failing tests should complete within the default timeout period
