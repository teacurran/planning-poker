# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T8",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Create integration tests for all Panache repositories using Testcontainers (PostgreSQL container). Write tests for: entity persistence (insert, update, delete), custom finder methods, relationship navigation, JSONB field serialization/deserialization, soft delete behavior (User, Room). Use Quarkus `@QuarkusTest` annotation with `@TestProfile` for test database configuration. Assert results using AssertJ or Rest Assured for fluent assertions.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Repository interfaces from I1.T7, Testcontainers setup patterns for PostgreSQL, Sample entity instances for testing",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/repository/*.java",
    "backend/src/main/java/com/scrumpoker/domain/**/*.java"
  ],
  "target_files": [
    "backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java",
    "backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java",
    "backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java",
    "backend/src/test/resources/application-test.properties"
  ],
  "deliverables": "12 repository test classes with minimum 3 test methods each (create, findById, custom finder), Testcontainers PostgreSQL configuration in test profile, Tests for JSONB field operations (Room.config, UserPreference.default_room_config), Soft delete tests verifying `deleted_at` timestamp behavior, Foreign key relationship tests (e.g., deleting User cascades to UserPreference)",
  "acceptance_criteria": "`mvn test` executes all repository tests successfully, Testcontainers starts PostgreSQL container automatically, All CRUD operations pass (insert, select, update, delete), Custom finder methods return expected results, JSONB fields round-trip correctly (save and retrieve complex objects), Soft delete tests confirm `deleted_at` set correctly, Test coverage >80% for repository classes",
  "dependencies": ["I1.T7"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: integration-testing (from 03_Verification_and_Glossary.md)

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

### Context: testing-levels (from 03_Verification_and_Glossary.md)

```markdown
### 5.1. Testing Levels

The project employs a comprehensive testing strategy following the test pyramid model to ensure quality at all levels:

#### Unit Testing

**Scope:** Individual classes and methods in isolation (services, utilities, validators)

**Framework:** JUnit 5 (backend), Jest/Vitest (frontend)

**Coverage Target:** >90% code coverage for service layer, >80% for overall codebase

**Approach:**
- Mock external dependencies (repositories, adapters, external services) using Mockito
- Test business logic thoroughly (happy paths, edge cases, error scenarios)
- Fast execution (<5 minutes for entire unit test suite)
- Run on every developer commit and in CI pipeline

**Examples:**
- `RoomServiceTest`: Tests room creation with unique ID generation, config validation, soft delete
- `VotingServiceTest`: Tests vote casting, consensus calculation with known inputs
- `BillingServiceTest`: Tests subscription tier transitions, Stripe integration mocking

**Acceptance Criteria:**
- All unit tests pass (`mvn test`, `npm run test:unit`)
- Coverage reports meet targets (verify with JaCoCo, Istanbul)
- No flaky tests (consistent results across runs)
```

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

The ERD section provides detailed entity specifications for:
- **User Entity**: UUID primary key, email (unique index), oauth_provider, oauth_subject, subscription_tier, deleted_at (soft delete)
- **Room Entity**: String(6) primary key (nanoid), owner_id FK to User, organization_id FK (optional), config JSONB, privacy_mode enum, deleted_at (soft delete)
- **Vote Entity**: UUID primary key, round_id FK, participant_id FK, card_value VARCHAR(10), voted_at timestamp
- **UserPreference Entity**: UUID primary key, user_id FK (unique), default_room_config JSONB, notification_settings JSONB
- **Round Entity**: UUID primary key, room_id FK, round_number INT, story_title, consensus JSONB
- **Organization Entity**: UUID primary key, name, domain, sso_config JSONB, branding JSONB
- **Relationships**: User → Room (owner), Room → Organization (many-to-one), Vote → Round/Participant (many-to-one)

### Context: Testcontainers (from Glossary)

```markdown
| **Testcontainers** | Java library providing lightweight, disposable database/cache containers for integration tests |
| **Uni** | Mutiny type representing asynchronous single-item result (similar to CompletableFuture) |
| **Multi** | Mutiny type representing asynchronous stream of 0-N items (similar to Reactive Streams Publisher) |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** **EXCELLENT REFERENCE TEMPLATE** - This file demonstrates the EXACT pattern you MUST follow for all remaining repository tests. It uses `@QuarkusTest`, `@RunOnVertxContext` with `UniAsserter`, Panache transactions, and AssertJ assertions. **STATUS: COMPLETE - Use as golden reference!**
    *   **Recommendation:** You MUST replicate this testing pattern for all other repositories. Key elements to copy:
        - `@QuarkusTest` class-level annotation
        - `@RunOnVertxContext` on test methods with `UniAsserter` parameter
        - `@BeforeEach` setup method using `Panache.withTransaction(() -> repository.deleteAll())`
        - Test method structure: `asserter.execute()` for actions, `asserter.assertThat()` for assertions
        - Helper methods for creating test entities at bottom of class
        - Tests cover: persist/findById, custom finders, relationships, soft delete behavior

*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** **EXCELLENT REFERENCE for JSONB and relationship testing** - Shows how to test JSONB fields (Room.config) and foreign key relationships (Room → Owner, Room → Organization). Uses `@Transactional` instead of `@RunOnVertxContext` (synchronous blocking approach). **STATUS: COMPLETE**
    *   **Recommendation:** Use this as reference for testing JSONB serialization/deserialization and relationship navigation. Note the pattern for verifying relationship navigation: `assertThat(found.owner).isNotNull(); User owner = found.owner; assertThat(owner.userId).isEqualTo(testOwner.userId);`

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** **EXCELLENT REFERENCE for complex queries and multi-entity relationships** - Demonstrates testing repositories with multiple foreign keys (Vote → Round, Vote → Participant), ordering queries (by `votedAt`), and special character handling (?, ∞, ☕). Uses `@Transactional`. **STATUS: COMPLETE**
    *   **Recommendation:** Use this as reference for repositories with complex relationships requiring setup of multiple dependent entities. Notice the comprehensive setup in `@BeforeEach` creating entire entity graph (User → Room → Round, Participant).

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Test configuration file that enables Testcontainers Dev Services by NOT setting explicit datasource URLs. Configures Flyway migrations, logging levels, and disables OIDC for tests.
    *   **Recommendation:** This file is already correctly configured for Testcontainers. DO NOT modify it. The key insight is that by NOT setting `quarkus.datasource.reactive-url`, Quarkus automatically starts a Testcontainers PostgreSQL instance.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven build configuration with all required testing dependencies: `quarkus-junit5`, `quarkus-test-vertx`, `rest-assured`, and `assertj-core` (3.24.2).
    *   **Recommendation:** You have all necessary dependencies. Use AssertJ for fluent assertions (`assertThat()`), REST Assured for HTTP testing (future iterations), and Quarkus Test Vert.x for reactive testing patterns.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** Example repository implementing `PanacheRepositoryBase<User, UUID>` with custom finder methods: `findByEmail()`, `findByOAuthProviderAndSubject()`, `findActiveByEmail()`, `countActive()`.
    *   **Recommendation:** All repositories follow this pattern. When testing, ensure you test ALL custom finder methods defined in each repository. Use Panache query syntax like `find("deletedAt is null")` for soft delete filtering.

### Implementation Tips & Notes

*   **Tip #1 - Testing Pattern Choice**: The codebase shows TWO testing approaches:
    - **Reactive Pattern** (`@RunOnVertxContext` + `UniAsserter`): Used in UserRepositoryTest - required when testing reactive Uni/Multi return types
    - **Blocking Pattern** (`@Transactional`): Used in RoomRepositoryTest and VoteRepositoryTest - simpler syntax using `.await().indefinitely()`

    **RECOMMENDATION**: Since all repository methods return `Uni<>` types, I recommend using the **Blocking Pattern** (`@Transactional`) for remaining tests as it's cleaner and all existing complete tests use it. The UserRepositoryTest reactive pattern works but is more verbose.

*   **Tip #2 - Testcontainers Activation**: Testcontainers PostgreSQL starts automatically because `application.properties` does NOT define explicit datasource URLs. Quarkus "Dev Services" feature detects this and launches a container. You do NOT need to manually configure Testcontainers.

*   **Tip #3 - Database Cleanup**: Use `repository.deleteAll().await().indefinitely()` in `@BeforeEach` to ensure test isolation. Call this for ALL repositories that might have cascading relationships. **CRITICAL**: Delete in reverse dependency order (e.g., delete Votes before Rounds before Rooms).

*   **Tip #4 - JSONB Testing**: For entities with JSONB fields (Room.config, UserPreference.default_room_config, Organization.sso_config), test round-trip persistence:
    ```java
    String jsonConfig = "{\"deckType\":\"fibonacci\",\"timerEnabled\":true}";
    room.config = jsonConfig;
    repository.persist(room).await().indefinitely();
    Room found = repository.findById(room.roomId).await().indefinitely();
    assertThat(found.config).isEqualTo(jsonConfig);
    assertThat(found.config).contains("fibonacci"); // Verify content
    ```

*   **Tip #5 - Soft Delete Testing**: For User and Room entities, test soft delete by setting `deletedAt = Instant.now()` and verify:
    1. Entity still exists when querying by ID
    2. Entity excluded from "active" queries (e.g., `findActiveByEmail()`)
    3. Count methods distinguish between total and active entities

*   **Tip #6 - Foreign Key Relationship Testing**: Test bidirectional navigation:
    ```java
    // Test FK from Room to Owner
    Room found = roomRepository.findById("room01").await().indefinitely();
    assertThat(found.owner).isNotNull();
    assertThat(found.owner.email).isEqualTo("owner@example.com");
    ```

*   **Tip #7 - Entity ID Generation**:
    - **UUID entities** (User, Round, Vote, etc.): Set `entity.id = UUID.randomUUID()` manually in test helpers
    - **String entities** (Room with nanoid): Provide explicit 6-character roomId in tests (e.g., "room01", "test01")

*   **Tip #8 - Test Execution**: Run tests with `mvn test`. Testcontainers will download PostgreSQL Docker image on first run (may take 2-3 minutes). Subsequent runs reuse the image. Expect ~10-20 seconds startup time per test class for container initialization.

*   **Warning #1 - Circular Dependencies**: When creating test entities with relationships, be careful of circular references. Create entities in dependency order: User → Organization → Room → Participant → Round → Vote. Persist parent entities BEFORE setting FK references.

*   **Warning #2 - Reactive Types**: All Panache repository methods return `Uni<>`. You MUST call `.await().indefinitely()` to block and get the actual result in `@Transactional` tests. Forgetting this will cause compilation errors.

*   **Warning #3 - AssertJ vs JUnit Assertions**: The project uses AssertJ (`assertThat()`), NOT JUnit assertions (`assertEquals()`). Use AssertJ's fluent API:
    - `assertThat(value).isEqualTo(expected)`
    - `assertThat(list).hasSize(3)`
    - `assertThat(object).isNotNull()`
    - `assertThat(list).extracting(Entity::getId).containsExactlyInAnyOrder("id1", "id2")`

### Missing Tests - Action Required

The following repository test files exist but are INCOMPLETE (based on surefire reports showing 0 tests or failures):

**High Priority** (Core entities with JSONB and relationships):
1. ~~`UserRepositoryTest.java`~~ - ✅ COMPLETE (has 10 passing tests)
2. ~~`RoomRepositoryTest.java`~~ - ✅ COMPLETE (has 14 passing tests)
3. ~~`VoteRepositoryTest.java`~~ - ✅ COMPLETE (has 11 passing tests)
4. `RoundRepositoryTest.java` - **NEEDS TESTS** (tests relationships to Room, Vote aggregation)
5. `RoomParticipantRepositoryTest.java` - **NEEDS TESTS** (tests Room/User relationships)
6. `UserPreferenceRepositoryTest.java` - **NEEDS TESTS** (tests JSONB notification_settings, default_room_config)

**Medium Priority** (Enterprise features):
7. `OrganizationRepositoryTest.java` - **NEEDS TESTS** (tests JSONB sso_config, branding)
8. `OrgMemberRepositoryTest.java` - **NEEDS TESTS** (composite key entity)

**Lower Priority** (Supporting entities):
9. `SessionHistoryRepositoryTest.java` - **NEEDS TESTS** (partitioned table queries)
10. `SubscriptionRepositoryTest.java` - **NEEDS TESTS** (User relationship, status enums)
11. `PaymentHistoryRepositoryTest.java` - **NEEDS TESTS** (Subscription relationship)
12. `AuditLogRepositoryTest.java` - **NEEDS TESTS** (partitioned table with composite key)

### Recommended Execution Order

Complete tests in this order to handle dependencies:
1. **UserPreferenceRepositoryTest** (depends only on User, which is complete)
2. **RoundRepositoryTest** (depends on User + Room, both complete)
3. **RoomParticipantRepositoryTest** (depends on User + Room)
4. **SubscriptionRepositoryTest** (depends on User)
5. **OrganizationRepositoryTest** (independent entity with JSONB)
6. **OrgMemberRepositoryTest** (depends on Organization + User)
7. **PaymentHistoryRepositoryTest** (depends on Subscription)
8. **SessionHistoryRepositoryTest** (depends on Room, partitioned table)
9. **AuditLogRepositoryTest** (depends on Organization, partitioned table)

### Test Template Structure

Use this structure for each repository test class:

```java
@QuarkusTest
class XxxRepositoryTest {

    @Inject XxxRepository xxxRepository;
    @Inject [dependency repositories...]

    private [dependent entities...]

    @BeforeEach
    @Transactional
    void setUp() {
        // Delete all in reverse dependency order
        xxxRepository.deleteAll().await().indefinitely();
        // ... other cleanups

        // Create and persist dependent entities
        testEntity = createTestEntity();
        repository.persist(testEntity).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given, When, Then structure with AssertJ assertions
    }

    @Test
    @Transactional
    void testCustomFinderMethod() {
        // Test each custom finder from repository
    }

    @Test
    @Transactional
    void testRelationshipNavigation() {
        // Test foreign key relationships
    }

    @Test
    @Transactional
    void testJsonbFieldRoundTrip() {
        // If entity has JSONB fields
    }

    @Test
    @Transactional
    void testSoftDelete() {
        // If entity has deletedAt field
    }

    // Helper methods at bottom
    private Xxx createTestEntity() {
        Xxx entity = new Xxx();
        entity.id = UUID.randomUUID(); // or explicit String ID
        // ... set required fields
        return entity;
    }
}
```

---

## 4. Specific Task Guidance

**YOUR OBJECTIVE**: Write comprehensive integration tests for ALL 12 repository classes (9 remaining) following the established patterns.

**APPROACH**:
1. Start with high-priority repositories (UserPreference, Round, RoomParticipant)
2. For each repository, write minimum 3 test methods:
   - Basic CRUD (persist, findById, update, delete)
   - All custom finder methods from the repository class
   - Relationship navigation tests
   - JSONB field tests (if applicable)
   - Soft delete tests (if applicable - User and Room only)

**CRITICAL SUCCESS FACTORS**:
- ✅ Use `@QuarkusTest` and `@Transactional` annotations
- ✅ Use AssertJ assertions (`assertThat()`)
- ✅ Call `.await().indefinitely()` on all Uni<> return types
- ✅ Create helper methods for test entity creation
- ✅ Clean up database in `@BeforeEach` using `deleteAll()`
- ✅ Test ALL custom finder methods defined in each repository
- ✅ Achieve >80% test coverage for repository classes

**VERIFICATION**:
Run `mvn test` after implementing each repository test. All tests must pass. Use `mvn verify` to run the full test suite including integration tests.

**ESTIMATED EFFORT**:
- ~30-45 minutes per repository test class
- ~6-8 hours total for all 9 remaining repositories
- Fastest: SubscriptionRepository (simple entity, few finders)
- Most complex: SessionHistoryRepository, AuditLogRepository (partitioned tables with composite keys)

Good luck! Follow the patterns from UserRepositoryTest, RoomRepositoryTest, and VoteRepositoryTest precisely, and you will succeed. 🚀
