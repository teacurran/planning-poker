# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create integration tests for all Panache repositories using Testcontainers (PostgreSQL container). Write tests for: entity persistence (insert, update, delete), custom finder methods, relationship navigation, JSONB field serialization/deserialization, soft delete behavior (User, Room). Use Quarkus `@QuarkusTest` annotation with `@TestProfile` for test database configuration. Assert results using AssertJ or Rest Assured for fluent assertions.

**Acceptance Criteria:**
- `mvn test` executes all repository tests successfully
- Testcontainers starts PostgreSQL container automatically
- All CRUD operations pass (insert, select, update, delete)
- Custom finder methods return expected results
- JSONB fields round-trip correctly (save and retrieve complex objects)
- Soft delete tests confirm `deleted_at` set correctly
- Test coverage >80% for repository classes

---

## Issues Detected

### Critical Issue: @BeforeEach with UniAsserter Pattern

All 12 repository test files are failing with the error:
```
java.lang.IllegalStateException: No current Vertx context found
```

**Affected Files:**
- `UserRepositoryTest.java` (line 30-38)
- `RoomRepositoryTest.java` (line 41-49)
- `VoteRepositoryTest.java` (line 46-60)
- `SessionHistoryRepositoryTest.java` (line 40-48)
- `SubscriptionRepositoryTest.java` (line 36-44)
- `PaymentHistoryRepositoryTest.java` (line 33-41)
- `OrganizationRepositoryTest.java` (line 25-33)
- `OrgMemberRepositoryTest.java` (line 39-49)
- `RoomParticipantRepositoryTest.java` (line 39-49)
- `RoundRepositoryTest.java` (line 38-48)
- `UserPreferenceRepositoryTest.java` (line 31-39)
- `AuditLogRepositoryTest.java` (line 40-48)

**Root Cause:**
The `@BeforeEach` lifecycle methods are annotated with `@RunOnVertxContext` and use `UniAsserter` as a parameter. This pattern is **NOT SUPPORTED** by Quarkus test framework. The `@RunOnVertxContext` and `UniAsserter` are only valid for test methods (`@Test`), not lifecycle methods (`@BeforeEach`, `@AfterEach`).

**Current (Incorrect) Pattern:**
```java
@BeforeEach
@RunOnVertxContext
void setUp(UniAsserter asserter) {
    asserter.execute(() -> Panache.withTransaction(() -> repository.deleteAll()));
    testEntity = createTestEntity();
}
```

**Error Message:**
```
[ERROR] UserRepositoryTest.setUp:34 » IllegalState No current Vertx context found
```

**Total Test Results:**
- Tests run: 94
- Failures: 0
- Errors: 83
- Skipped: 0

83 out of 94 tests are failing due to this single pattern issue across all test files.

---

## Best Approach to Fix

### Solution: Remove @BeforeEach Setup and Initialize Data in Each Test

For reactive Panache tests with `UniAsserter`, you **MUST NOT** use `@BeforeEach` with `UniAsserter`. Instead, you have two options:

**Option 1 (RECOMMENDED): Initialize test data within each test method**

Remove the `@BeforeEach` method entirely and initialize test data as the first step in each test method using `asserter.execute()`.

**Example Fix for UserRepositoryTest.java:**

```java
@QuarkusTest
class UserRepositoryTest {

    @Inject
    UserRepository userRepository;

    // Remove @BeforeEach method entirely

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Clean up and initialize test data as first step
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));

        // Given: a new user
        User user = createTestUser("john@example.com", "github", "github-456");

        // When: persisting the user
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(user)));

        // Then: the user can be retrieved by ID
        asserter.assertThat(() -> Panache.withTransaction(() -> userRepository.findById(user.userId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.email).isEqualTo("john@example.com");
            // ... more assertions
        });
    }

    // Repeat for all test methods

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }
}
```

**Option 2 (ALTERNATIVE): Use @BeforeAll with blocking calls**

If you want to keep shared setup logic, use `@BeforeAll` with blocking reactive calls. However, this approach is less isolated and not recommended for tests that modify data.

```java
@BeforeAll
static void setupDatabase() {
    // Use blocking calls with await().indefinitely()
    // This is less ideal because it runs once for all tests
}
```

### Implementation Instructions

You MUST fix ALL 12 repository test files:

1. **UserRepositoryTest.java** - Remove `@BeforeEach` method (lines 30-38). Add cleanup as first step in each test.
2. **RoomRepositoryTest.java** - Remove `@BeforeEach` method (lines 41-49). Add cleanup + user/org creation in each test.
3. **VoteRepositoryTest.java** - Remove `@BeforeEach` method (lines 46-60). Add cleanup + room/round/participant setup in each test.
4. **SessionHistoryRepositoryTest.java** - Remove `@BeforeEach` method (lines 40-48). Add cleanup + room setup in each test.
5. **SubscriptionRepositoryTest.java** - Remove `@BeforeEach` method (lines 36-44). Add cleanup + user setup in each test.
6. **PaymentHistoryRepositoryTest.java** - Remove `@BeforeEach` method (lines 33-41). Add cleanup + user/subscription setup in each test.
7. **OrganizationRepositoryTest.java** - Remove `@BeforeEach` method (lines 25-33). Add cleanup in each test.
8. **OrgMemberRepositoryTest.java** - Remove `@BeforeEach` method (lines 39-49). Add cleanup + org/user setup in each test.
9. **RoomParticipantRepositoryTest.java** - Remove `@BeforeEach` method (lines 39-49). Add cleanup + room setup in each test.
10. **RoundRepositoryTest.java** - Remove `@BeforeEach` method (lines 38-48). Add cleanup + room setup in each test.
11. **UserPreferenceRepositoryTest.java** - Remove `@BeforeEach` method (lines 31-39). Add cleanup + user setup in each test.
12. **AuditLogRepositoryTest.java** - Remove `@BeforeEach` method (lines 40-48). Add cleanup in each test.

### Key Points to Remember

- **NEVER** use `@RunOnVertxContext` or `UniAsserter` in `@BeforeEach` or `@AfterEach` methods
- **ALWAYS** perform cleanup and data initialization as the first `asserter.execute()` call in each test method
- Keep helper methods (like `createTestUser()`) as non-reactive plain Java methods
- Use `Panache.withTransaction()` wrapper for all database operations in tests
- Each test should be independent and not rely on shared mutable state from `@BeforeEach`

### Testing After Fix

After fixing all test files, run:

```bash
cd backend
mvn clean test
```

**Expected result:** All 94 tests should pass with 0 errors.

---

## Additional Notes

The test code is otherwise well-structured with:
- Comprehensive coverage of CRUD operations
- Custom finder method tests
- JSONB field serialization tests
- Soft delete behavior tests
- Relationship navigation tests
- AssertJ fluent assertions

The only issue is the incorrect use of `@BeforeEach` with reactive test patterns. Once fixed, the test suite will meet all acceptance criteria.
