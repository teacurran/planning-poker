# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration test for SSO authentication flow using mock IdP. Test OIDC: mock authorization server, valid ID token, callback processes successfully, user created (JIT provisioning), org assignment, JWT tokens returned. Test SAML2: mock SAML response, assertion validated, user provisioned, tokens returned. Test audit log entry creation. Use Testcontainers for PostgreSQL.

---

## Issues Detected

*   **Test Failure:** The test methods `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg` and `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership` are failing with `IllegalStateException: Hibernate Reactive Panache requires a safe (isolated) Vert.x sub-context, but the current context hasn't been flagged as such`.

*   **Root Cause:** The `runInVertxContext` helper method at lines 395-413 of `SsoAuthenticationIntegrationTest.java` creates a duplicated Vert.x context using `VertxContext.getOrCreateDuplicatedContext(vertx)`, but this context is NOT properly marked as "safe" for Hibernate Reactive Panache operations.

*   **Pattern Error:** Hibernate Reactive Panache requires contexts to be explicitly marked as safe using `VertxContextSafetyToggle.setContextSafe()`. The helper method creates a duplicated context but doesn't flag it as safe, causing all `Panache.withTransaction()` calls to fail.

*   **Impact:** Two test methods that perform database setup and assertions outside of `@RunOnVertxContext` are failing because they can't execute Panache operations in the test-created context.

---

## Best Approach to Fix

You MUST modify the `runInVertxContext` helper method in `backend/src/test/java/com/scrumpoker/api/rest/SsoAuthenticationIntegrationTest.java` to properly mark the duplicated context as safe for Panache operations.

### Option 1: Mark Context as Safe (Recommended)

Replace the `runInVertxContext` method (lines 395-413) with this corrected version:

```java
/**
 * Executes a reactive Uni operation in a Vert.x duplicated context and blocks until completion.
 * This allows running reactive Panache operations from regular test methods (without @RunOnVertxContext).
 *
 * @param <T> The type of result returned by the Uni
 * @param supplier The Uni supplier to execute
 * @return The result of the Uni operation
 */
private <T> T runInVertxContext(java.util.function.Supplier<Uni<T>> supplier) {
    // Create a duplicated context (safe/isolated for Hibernate Reactive Panache)
    Context context = VertxContext.getOrCreateDuplicatedContext(vertx);

    // CRITICAL: Mark the context as safe for Hibernate Reactive Panache
    VertxContextSafetyToggle.setContextSafe(context, true);

    // Create a Promise to capture the result
    Promise<T> promise = Promise.promise();

    // Run the Uni supplier on the duplicated context
    context.runOnContext(v -> {
        supplier.get()
            .subscribe().with(
                result -> promise.complete(result),
                error -> promise.fail(error)
            );
    });

    // Block and wait for the result
    return promise.future().toCompletionStage().toCompletableFuture().join();
}
```

**Required Import:** Add this import at the top of the file:
```java
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
```

### Option 2: Alternative Simpler Approach

If `VertxContextSafetyToggle` is not accessible (it's an internal Quarkus class), use this alternative that wraps operations in `asserter.execute()` instead:

**Step 1:** Change test methods back to using `@RunOnVertxContext` with `UniAsserter`:

```java
@Test
@RunOnVertxContext
public void testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg(UniAsserter asserter) {
    // REST Assured call (NO asserter.execute - runs blocking on test thread before Vert.x context)
    ...REST call code...

    // Database assertions (wrapped in asserter.execute)
    asserter.execute(() -> Panache.withTransaction(() ->
        userRepository.findByOAuthProviderAndSubject("sso_oidc", TEST_SSO_SUBJECT)
            .invoke(user -> {
                assertThat(user).isNotNull();
                // ... assertions
            })
    ));
}
```

**Step 2:** Remove the `runInVertxContext` helper method and `Vertx vertx` injection - they're not needed.

---

## Recommendation

**Use Option 1** (mark context as safe) because:
1. It allows test methods to remain as simple `@Test` methods without `@RunOnVertxContext`
2. REST Assured HTTP calls run on regular test threads (no deadlock risk)
3. Database operations run in properly marked Vert.x contexts
4. Cleaner test code with less boilerplate

After fixing, run `mvn test -Dtest=SsoAuthenticationIntegrationTest` to verify all tests pass.

---

## Additional Notes

*   **No SAML2 tests needed:** The task description mentions SAML2 tests, but the codebase does not have SAML2 support implemented yet (see `SsoAdapter.java` lines 121-126). The TODO section at lines 369-381 correctly documents this. Do NOT implement SAML2 tests.

*   **Test coverage is sufficient:** The existing 6 OIDC tests cover all required scenarios: first login with JIT provisioning, returning user (no duplicate membership), missing email, unknown domain, missing code verifier, and domain mismatch.

*   **Audit logging works:** The test logs show audit log entries are being created successfully. The 500ms delay pattern is correct for async audit log verification.
