# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration test for SSO authentication flow using mock IdP. Test OIDC: mock authorization server, valid ID token, callback processes successfully, user created (JIT provisioning), org assignment, JWT tokens returned. Test SAML2: mock SAML response, assertion validated, user provisioned, tokens returned. Test audit log entry creation. Use Testcontainers for PostgreSQL.

---

## Issues Detected

*   **Test Failure:** The tests `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg` and `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership` are failing with `SocketTimeoutException: Read timed out` caused by thread blocking on the Vert.x event loop.
*   **Root Cause:** The test methods that make REST Assured HTTP calls are annotated with `@RunOnVertxContext`. REST Assured makes blocking HTTP calls, and when these run on the Vert.x event loop (due to `@RunOnVertxContext`), they cause deadlock/thread blocking.
*   **Pattern Error:** The git diff shows REST Assured calls were correctly unwrapped from `asserter.execute()`, but the test methods themselves still have `@RunOnVertxContext` annotation, which causes the entire method (including REST Assured calls) to run on the event loop.

---

## Best Approach to Fix

You MUST modify the test methods in `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java` to follow this pattern:

1. **Remove `@RunOnVertxContext` from test methods that make REST Assured HTTP calls.** Only keep `@Test` annotation.

2. **Keep `@RunOnVertxContext` ONLY on `@BeforeEach setUp()` method** - this is correct because it only does database operations via Panache transactions.

3. **For database assertions AFTER REST calls**, wrap them in `asserter.execute()` but execute the REST call BEFORE, outside of any Vert.x context:

```java
@Test  // NO @RunOnVertxContext here!
public void testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg() {
    // Given: setup (already done in @BeforeEach)

    SsoCallbackRequest request = new SsoCallbackRequest();
    request.code = "mock-authorization-code";
    request.protocol = "oidc";
    request.redirectUri = "https://app.scrumpoker.com/auth/callback";
    request.codeVerifier = "mock-code-verifier-12345";
    request.email = TEST_USER_EMAIL;

    // When: Call SSO callback endpoint (blocking HTTP call - NOT on event loop)
    given()
        .contentType(ContentType.JSON)
        .body(request)
        .header("X-Forwarded-For", "192.168.1.100")
        .header("User-Agent", "Mozilla/5.0 Test Browser")
    .when()
        .post("/api/v1/auth/sso/callback")
    .then()
        .statusCode(200)
        .body("accessToken", notNullValue())
        .body("refreshToken", notNullValue())
        .body("user.email", equalTo(TEST_USER_EMAIL))
        .body("user.displayName", equalTo(TEST_USER_NAME))
        .body("user.subscriptionTier", equalTo("FREE"));

    // Then: Verify user was created - use blocking wait for Uni result
    User user = userRepository.findByOAuthProviderAndSubject("sso_oidc", TEST_SSO_SUBJECT)
        .await().indefinitely();
    assertThat(user).isNotNull();
    assertThat(user.email).isEqualTo(TEST_USER_EMAIL);
    assertThat(user.displayName).isEqualTo(TEST_USER_NAME);
    // ... more assertions
}
```

4. **For async operations like audit logging**, use `Thread.sleep()` followed by `.await().indefinitely()`:

```java
// Give async audit logging time to complete
Thread.sleep(500);

// Query audit log
AuditLog auditLog = auditLogRepository.listAll()
    .map(logs -> logs.stream()
        .filter(log -> "SSO_LOGIN".equals(log.action))
        .findFirst()
        .orElse(null))
    .await().indefinitely();

assertThat(auditLog).isNotNull();
```

5. **Apply this pattern to ALL test methods** that make REST Assured calls:
   - `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg`
   - `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership`

6. **Keep tests WITHOUT REST calls unchanged** (they don't use `@RunOnVertxContext` anyway):
   - `testOidcSsoCallback_MissingEmail_Returns400`
   - `testOidcSsoCallback_UnknownDomain_Returns401`
   - `testOidcSsoCallback_MissingCodeVerifier_Returns400`
   - `testOidcSsoCallback_DomainMismatch_Returns401`

7. **Do NOT implement SAML2 tests yet** - the task description says SAML2 is planned but not yet implemented. The TODO section at lines 377-390 is correct.

8. **After fixing, run `mvn clean verify` from the backend directory** to ensure all tests pass.

---

## Key Principle

**NEVER use `@RunOnVertxContext` on test methods that make blocking HTTP calls with REST Assured.** The Vert.x event loop is for non-blocking reactive operations only. REST Assured is blocking and must run on regular test threads. Use `.await().indefinitely()` to block and wait for reactive Uni results in test code.
