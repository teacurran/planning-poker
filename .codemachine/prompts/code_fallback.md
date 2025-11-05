# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration test for SSO authentication flow using mock IdP. Test OIDC: mock authorization server, valid ID token, callback processes successfully, user created (JIT provisioning), org assignment, JWT tokens returned. Test SAML2: mock SAML response, assertion validated, user provisioned, tokens returned. Test audit log entry creation. Use Testcontainers for PostgreSQL.

---

## Issues Detected

*   **Test Failure:** The tests `testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg`, `testOidcSsoCallback_ReturningUser_DoesNotDuplicateOrgMembership`, and `testOidcSsoCallback_DomainMismatch_Returns401` are all failing with 500 errors instead of the expected responses (200 OK or 401).

*   **Root Cause:** The `MockSsoAdapter` is NOT being properly injected. The real `SsoAdapter` is being used instead, which attempts to make actual HTTP calls to the IdP, resulting in errors like "Failed to exchange authorization code". This happens because:
  1. The `MockSsoAdapter` class doesn't properly replace the `SsoAdapter` bean
  2. The `@Alternative` and `@Priority` annotations on `MockSsoAdapter` are not sufficient
  3. The `AuthController` injects `SsoAdapter` (line 58), but CDI is not replacing it with the mock

---

## Best Approach to Fix

You MUST modify the `SsoAuthenticationIntegrationTest.java` to properly mock the `SsoAdapter` bean. The current approach with `@Alternative` is not working.

**Option 1 (Recommended): Use Quarkus `@InjectMock`**

Replace the `MockSsoAdapter` alternative bean approach with Quarkus's `@InjectMock` annotation:

1. Remove the `MockSsoAdapter` inner class entirely
2. Remove the `getEnabledAlternatives()` method from `SsoTestProfile`
3. Add an `@InjectMock` field for `SsoAdapter` in the test class
4. In `@BeforeEach` or individual test methods, use Mockito to stub the `authenticate()` method:
   ```java
   when(ssoAdapter.authenticate(anyString(), anyString(), any(), any()))
       .thenReturn(Uni.createFrom().item(mockSsoUserInfo));
   ```
5. For different test scenarios (domain mismatch, etc.), change the stubbing behavior

**Option 2: Use `@QuarkusMock` programmatically**

Use Quarkus's `QuarkusMock.installMockForType()` to replace the SsoAdapter bean:

1. Keep the `MockSsoAdapter` class but make it extend or implement a common interface
2. Use `QuarkusMock.installMockForType(mockInstance, SsoAdapter.class)` in `@BeforeEach`
3. Reset the mock between tests

**Option 3: Create a test-scoped CDI producer**

Create a test bean producer that produces a mocked `SsoAdapter`:

1. Create a `@ApplicationScoped` test configuration class with `@Alternative` and higher priority
2. Add a `@Produces` method that returns a mocked `SsoAdapter`
3. Enable the alternative in the test profile

**Recommended Implementation (Option 1):**

```java
@QuarkusTest
@TestProfile(SsoAuthenticationIntegrationTest.SsoTestProfile.class)
public class SsoAuthenticationIntegrationTest {

    @InjectMock
    SsoAdapter ssoAdapter;

    @Inject
    OrganizationRepository organizationRepository;

    // ... other injections ...

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up test data
        asserter.execute(() -> Panache.withTransaction(() ->
            // ... cleanup code ...
        ));

        // Create test organization
        asserter.execute(() -> Panache.withTransaction(() -> {
            testOrganization = new Organization();
            // ... setup code ...
            return organizationRepository.persist(testOrganization);
        }));

        // Setup default mock behavior for successful authentication
        SsoUserInfo successUserInfo = new SsoUserInfo(
            TEST_SSO_SUBJECT,
            TEST_USER_EMAIL,
            TEST_USER_NAME,
            "oidc",
            testOrganization.orgId
        );
        when(ssoAdapter.authenticate(anyString(), anyString(), any(), any()))
            .thenReturn(Uni.createFrom().item(successUserInfo));
    }

    @Test
    @RunOnVertxContext
    public void testOidcSsoCallback_FirstLogin_CreatesUserAndAssignsToOrg(UniAsserter asserter) {
        // Default mock behavior already set in @BeforeEach

        // When: Call SSO callback endpoint
        // ... test code ...
    }

    @Test
    @RunOnVertxContext
    public void testOidcSsoCallback_DomainMismatch_Returns401(UniAsserter asserter) {
        // Override mock behavior for this specific test
        SsoUserInfo mismatchUserInfo = new SsoUserInfo(
            "oidc-subject-mismatch",
            "hacker@evil.com",
            "Hacker User",
            "oidc",
            testOrganization.orgId
        );
        when(ssoAdapter.authenticate(anyString(), anyString(), any(), any()))
            .thenReturn(Uni.createFrom().item(mismatchUserInfo));

        // When/Then: Should return 401 for domain mismatch
        // ... test code ...
    }
}
```

**Additional Notes:**

- Make sure to import `io.quarkus.test.InjectMock` and Mockito's `when()`, `any()`, `anyString()`
- The `SsoTestProfile` can now be simplified - just extend `NoSecurityTestProfile` without overriding `getEnabledAlternatives()`
- Keep all other test logic the same (REST Assured calls, database assertions, audit log checks)
- The three failing tests should pass once the SsoAdapter is properly mocked
