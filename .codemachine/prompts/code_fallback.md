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

*   **Test Failure:** The tests `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg` and `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership` are failing with "SocketTimeout Read timed out" errors.
*   **Root Cause:** The refactored code removed the `asserter.execute()` wrapper around the REST Assured HTTP calls in lines 146-159 and 252-262, but the tests still use `@RunOnVertxContext` annotation and `UniAsserter` parameter. In Quarkus reactive tests with `@RunOnVertxContext`, all blocking operations (including REST Assured HTTP calls) MUST be wrapped in `asserter.execute()` to run on the correct Vert.x context.

---

## Best Approach to Fix

You MUST revert the REST Assured HTTP calls to be wrapped in `asserter.execute()` blocks in the following two test methods:

1. **File:** `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java`
   - **Line 146-159:** In `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg`, wrap the `given()...` REST Assured call in `asserter.execute(() -> ...)`
   - **Line 252-262:** In `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership`, wrap the `given()...` REST Assured call in `asserter.execute(() -> ...)`

The pattern should match the original implementation where REST Assured calls were wrapped like:
```java
asserter.execute(() ->
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
        // ... other assertions
);
```

Do NOT modify any other parts of the code. The Mockito-based mocking approach is correct and should be kept. The issue is only with the missing `asserter.execute()` wrappers around the HTTP calls.

After making these changes, run `mvn verify -Dtest=SsoAuthenticationIntegrationTest` to verify all 6 tests pass.
