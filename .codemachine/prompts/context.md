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

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

```markdown
### 3.6. Data Model Overview & ERD

#### Description

The data model follows a relational schema leveraging PostgreSQL's ACID properties for transactional consistency and JSONB columns for flexible configuration storage (room settings, deck definitions). The model is optimized for both transactional writes (vote casting, room creation) and analytical reads (session history, organizational reporting).

**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management

#### Key Entities

| Entity | Purpose | Key Attributes |
|--------|---------|----------------|
| **User** | Registered user account | `user_id` (PK), `email`, `oauth_provider`, `oauth_subject`, `display_name`, `avatar_url`, `subscription_tier`, `created_at`, `deleted_at` |
| **UserPreference** | Saved user defaults | `user_id` (FK), `default_deck_type`, `default_room_config` (JSONB), `theme`, `notification_settings` (JSONB) |
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |
| **Room** | Estimation session | `room_id` (PK, nanoid 6-char), `owner_id` (FK nullable for anonymous), `org_id` (FK nullable), `title`, `privacy_mode` (PUBLIC/INVITE_ONLY/ORG_RESTRICTED), `config` (JSONB: deck, rules, timer), `created_at`, `last_active_at`, `deleted_at` |
| **RoomParticipant** | Active session participants | `room_id` (FK), `user_id` (FK nullable), `anonymous_id`, `display_name`, `role` (HOST/VOTER/OBSERVER), `connected_at` |
| **Vote** | Individual estimation vote | `vote_id` (PK), `room_id` (FK), `round_number`, `participant_id`, `card_value`, `voted_at` |
| **Round** | Estimation round within session | `round_id` (PK), `room_id` (FK), `round_number`, `story_title`, `started_at`, `revealed_at`, `average`, `median`, `consensus_reached` |
| **SessionHistory** | Completed session record | `session_id` (PK), `room_id` (FK), `started_at`, `ended_at`, `total_rounds`, `total_stories`, `participants` (JSONB array), `summary_stats` (JSONB) |
| **Subscription** | Stripe subscription record | `subscription_id` (PK), `stripe_subscription_id`, `entity_id` (user_id or org_id), `entity_type` (USER/ORG), `tier` (FREE/PRO/PRO_PLUS/ENTERPRISE), `status`, `current_period_end`, `canceled_at` |
| **PaymentHistory** | Payment transaction log | `payment_id` (PK), `subscription_id` (FK), `stripe_invoice_id`, `amount`, `currency`, `status`, `paid_at` |
| **AuditLog** | Compliance and security audit trail | `log_id` (PK), `org_id` (FK nullable), `user_id` (FK nullable), `action`, `resource_type`, `resource_id`, `ip_address`, `user_agent`, `timestamp` |

#### Database Indexing Strategy

**High-Priority Indexes:**
- `User(email)` - OAuth login lookups
- `User(oauth_provider, oauth_subject)` - OAuth subject resolution
- `Room(owner_id, created_at DESC)` - User's recent rooms query
- `Room(org_id, last_active_at DESC)` - Organization room listing
- `RoomParticipant(room_id, connected_at)` - Active participants query
- `Vote(round_id, participant_id)` - Vote aggregation for reveal
- `Round(room_id, round_number)` - Round history retrieval
- `SessionHistory(started_at)` - Partition pruning for date-range queries
- `Subscription(entity_id, entity_type, status)` - Active subscription lookups
- `AuditLog(org_id, timestamp DESC)` - Enterprise audit trail queries

**Partitioning:**
- `SessionHistory` partitioned by `started_at` (monthly range partitions)
- `AuditLog` partitioned by `timestamp` (monthly range partitions)
```

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

### Context: integration-strategy (from 03_Verification_and_Glossary.md)

```markdown
### 5.5. Integration Strategy

**Component Integration:**

1. **Service Layer Integration:**
   - Services depend on repositories via constructor injection (testable)
   - Integration tests verify service → repository → database flow
   - Use Testcontainers for realistic database behavior

**Cross-Module Integration:**

1. **Backend ↔ Database:**
   - Hibernate Reactive Panache repositories
   - Integration tests with Testcontainers PostgreSQL
   - Migration scripts applied automatically in tests
```

### Context: Reactive Programming Glossary Terms

```markdown
| **Testcontainers** | Java library providing lightweight, disposable database/cache containers for integration tests |
| **Uni** | Mutiny type representing asynchronous single-item result (similar to CompletableFuture) |
| **Multi** | Mutiny type representing asynchronous stream of 0-N items (similar to Reactive Streams Publisher) |
| **Panache** | Quarkus extension simplifying Hibernate with active record or repository patterns |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** This file contains a comprehensive integration test suite for UserRepository demonstrating the exact patterns you MUST follow. It uses `@QuarkusTest` annotation with `@RunOnVertxContext` and `UniAsserter` for reactive testing. Tests cover CRUD operations, custom finders (`findByEmail()`, `findByOAuthProviderAndSubject()`), soft deletes, and active user queries.
    *   **Recommendation:** You MUST use this file as the PRIMARY template for all repository tests. Copy the test structure, assertions patterns, and reactive testing approach (UniAsserter). Note the `@BeforeEach` setup that cleans up test data using `Panache.withTransaction(() -> repository.deleteAll())`.
    *   **Key Patterns to Copy:**
        - Always use `@RunOnVertxContext` with `UniAsserter` for reactive tests
        - Wrap all database operations in `Panache.withTransaction()`
        - Use `asserter.execute()` for operations and `asserter.assertThat()` for assertions
        - Clean up data in `@BeforeEach` before each test
        - Use helper methods to create test entities (e.g., `createTestUser()`)
        - Test coverage includes: persist+findById, custom finders, update, soft delete, count operations

*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** This file demonstrates advanced integration testing for the Room entity, including relationship navigation (owner, organization), JSONB field serialization, soft deletes, and complex finder methods (`findActiveByOwnerId()`, `findPublicRooms()`, `findInactiveSince()`). It shows how to test entities with foreign key relationships and String IDs (nanoid pattern).
    *   **Recommendation:** You MUST study this file carefully for testing relationships and JSONB fields. Note how it injects multiple repositories (`UserRepository`, `OrganizationRepository`) to set up test data with proper foreign key relationships. The `testJsonbConfigField()` method (line 80-98) shows EXACTLY how to test JSONB serialization - this is the pattern you must use for all JSONB fields.
    *   **Key Patterns for JSONB Testing:**
        ```java
        String jsonConfig = "{\"deckType\":\"fibonacci\",\"timerEnabled\":true,\"timerDuration\":300}";
        room.config = jsonConfig;
        roomRepository.persist(room).await().indefinitely();
        Room found = roomRepository.findById("room02").await().indefinitely();
        assertThat(found.config).isEqualTo(jsonConfig);
        assertThat(found.config).contains("fibonacci"); // Verify specific content
        ```

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** This file shows integration testing with multiple entity relationships (Vote → Round → Room → User → Participant). It uses `@Transactional` instead of `@RunOnVertxContext`, demonstrating blocking API usage with `.await().indefinitely()` for simpler test code. Tests special card values (?, ∞, ☕) and ordering queries.
    *   **Recommendation:** Note that VoteRepositoryTest uses a DIFFERENT pattern - blocking reactive API with `@Transactional`. Both patterns are valid:
        - **Reactive Pattern** (`@RunOnVertxContext` + `UniAsserter`): More complex, fully async
        - **Blocking Pattern** (`@Transactional` + `.await().indefinitely()`): Simpler, easier to read
    *   **For consistency, I recommend using the Reactive Pattern** (like UserRepositoryTest) for all new tests, as it better aligns with Quarkus reactive programming model.

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Test configuration file that enables Quarkus Dev Services (Testcontainers) by NOT setting explicit database URLs. Flyway migrations are enabled to run at startup (`quarkus.flyway.migrate-at-start=true`). Logging is configured for debugging database operations.
    *   **Recommendation:** This file is ALREADY CORRECTLY CONFIGURED. You MUST NOT modify it. The key insight is that Testcontainers activation is AUTOMATIC when no database URLs are set. The Maven Surefire plugin (in pom.xml) explicitly sets empty environment variables to ensure Dev Services activate.
    *   **Critical Understanding:** DO NOT add `quarkus.datasource.reactive-url` or `quarkus.datasource.jdbc-url` to this file. Doing so will BREAK Testcontainers auto-configuration.

### Implementation Tips & Notes

*   **Tip:** I have identified 12 repository classes that need tests based on the task requirements. The existing tests (UserRepositoryTest, RoomRepositoryTest, VoteRepositoryTest) cover 3 repositories. You MUST create tests for the remaining 9 repositories:
    1. `UserPreferenceRepositoryTest` - Test JSONB fields (default_room_config, notification_settings), relationship to User
    2. `OrganizationRepositoryTest` - Test JSONB fields (sso_config, branding), basic CRUD
    3. `OrgMemberRepositoryTest` - Test composite primary key (org_id, user_id), relationships to User and Organization
    4. `RoomParticipantRepositoryTest` - Test relationships to Room and User, role enum, nullable user_id for anonymous participants
    5. `RoundRepositoryTest` - Test relationship to Room, round_number queries, consensus calculation fields
    6. `SessionHistoryRepositoryTest` - Test JSONB fields (participants, summary_stats), partitioning queries (by started_at), relationship to Room
    7. `SubscriptionRepositoryTest` - Test entity_type enum, tier enum, status queries, relationship to User or Organization
    8. `PaymentHistoryRepositoryTest` - Test relationship to Subscription, payment status tracking
    9. `AuditLogRepositoryTest` - Test nullable foreign keys (org_id, user_id), partitioning queries (by timestamp), INET data type for ip_address

*   **Note:** The reactive testing pattern using `UniAsserter` is MORE COMPLEX but BETTER aligns with Quarkus reactive programming model. The blocking pattern with `.await().indefinitely()` is SIMPLER but creates blocking operations in tests. For consistency with UserRepositoryTest (the most comprehensive example), you SHOULD use the reactive pattern for all new tests.

*   **Warning:** Test isolation is CRITICAL. Each test MUST clean up data in `@BeforeEach` using `repository.deleteAll()` wrapped in `Panache.withTransaction()`. Failure to clean up will cause test pollution and flaky tests. The order of cleanup matters - delete child entities BEFORE parent entities to avoid foreign key constraint violations.

*   **Tip:** For JSONB testing, follow the pattern from RoomRepositoryTest line 82-97:
    1. Create a JSON string with test data
    2. Assign to JSONB field
    3. Persist entity
    4. Retrieve entity
    5. Assert JSON string matches exactly
    6. Assert JSON contains expected keys/values using `.contains()`

*   **Tip:** For soft delete testing, follow UserRepositoryTest line 143-162:
    1. Persist entity
    2. Retrieve and set `deletedAt = Instant.now()`
    3. Persist again (update)
    4. Verify entity still exists when querying by ID
    5. Verify "active" queries exclude soft-deleted entities (see `testFindActiveByEmail()` line 166-185)

*   **Tip:** For relationship navigation testing, follow RoomRepositoryTest line 102-117:
    1. Persist parent entity (e.g., User)
    2. Create child with foreign key reference (e.g., Room with owner)
    3. Persist child
    4. Retrieve child
    5. Assert that navigating the relationship (e.g., `room.owner`) returns correct parent
    6. Assert parent's fields match expected values (e.g., `owner.email`)

*   **Warning:** Hibernate Reactive requires ALL database operations to be wrapped in `Panache.withTransaction()`. Forgetting this will cause errors like "Session/EntityManager is closed" or "No transaction in progress". The `UniAsserter.execute()` method handles this automatically when you pass a `Uni` supplier.

*   **Tip:** Use AssertJ's fluent assertions for readability:
    - `assertThat(found).isNotNull()` instead of `assertNotNull(found)`
    - `assertThat(list).hasSize(2)` instead of `assertEquals(2, list.size())`
    - `assertThat(users).extracting(u -> u.email).containsExactly("a@ex.com", "b@ex.com")`
    - These are MORE READABLE than JUnit's basic assertions and provide better error messages

*   **Note:** Testcontainers will automatically download and start a PostgreSQL container on the first test run. This can take 30-60 seconds initially but subsequent runs reuse the container (Testcontainers Desktop optimization). The Maven Surefire plugin configuration in pom.xml ensures the container is properly managed with JBoss LogManager integration.

*   **Tip:** For testing custom finder methods, use known test data and assert exact results. For example, if testing `findByRoundId(roundId)`, create 3 votes for that round and assert the result list has exactly 3 votes with expected card values. Use `containsExactlyInAnyOrder()` when order doesn't matter, `containsExactly()` when order is significant (e.g., for queries with ORDER BY).

*   **Warning:** When testing entities with composite keys (like OrgMember with org_id + user_id composite PK or AuditLog with composite key), you MUST set all key fields before persisting. Panache's `persist()` will NOT auto-generate composite keys. The entity classes use `@EmbeddedId` for composite keys - study OrgMemberId and AuditLogId classes for proper structure.

*   **Tip:** For testing partitioned tables (SessionHistory, AuditLog), your basic CRUD tests will work normally. The partitioning is transparent at the application layer. However, you SHOULD add a comment in the test class javadoc noting that these entities are partitioned by date, so future maintainers are aware. Example: `/** Tests for SessionHistoryRepository. Note: SessionHistory table is partitioned by started_at (monthly). */`

### Critical Success Criteria Checklist

**YOU MUST ACHIEVE ALL OF THE FOLLOWING:**

1. ✅ **Test Pattern Consistency:** All tests use `@QuarkusTest` + `@RunOnVertxContext` + `UniAsserter` (follow UserRepositoryTest pattern)
2. ✅ **Cleanup in BeforeEach:** Every test class has `@BeforeEach` method calling `repository.deleteAll()` wrapped in `Panache.withTransaction()`
3. ✅ **AssertJ Assertions:** All assertions use AssertJ (`assertThat()`) NOT JUnit (`assertEquals()`)
4. ✅ **Transaction Wrapping:** All database operations wrapped in `Panache.withTransaction()`
5. ✅ **Test Coverage:** Minimum 3 tests per repository (persist+findById, custom finder, relationship/JSONB/soft delete)
6. ✅ **JSONB Round-Trip:** Tests for entities with JSONB fields verify serialization/deserialization works correctly
7. ✅ **Soft Delete Verification:** Tests for User and Room verify `deletedAt` timestamp behavior and "active" query filtering
8. ✅ **Relationship Navigation:** Tests verify foreign key relationships can be navigated (e.g., `vote.round.room.owner`)
9. ✅ **Helper Methods:** Each test class has helper methods for creating test entities (e.g., `createTestUser()`)
10. ✅ **All Tests Pass:** `mvn test` executes without errors, Testcontainers starts successfully, all assertions pass
11. ✅ **Test Coverage Goal:** >80% code coverage for all repository classes (verify with JaCoCo report after running tests)

### Recommended Implementation Approach

**STEP 1:** Start with the simplest repository tests to build momentum:
- `PaymentHistoryRepositoryTest` (no JSONB, simple FK to Subscription)
- `SubscriptionRepositoryTest` (simple enums, FK to User)

**STEP 2:** Move to repositories with JSONB fields:
- `UserPreferenceRepositoryTest` (JSONB: default_room_config, notification_settings)
- `OrganizationRepositoryTest` (JSONB: sso_config, branding)

**STEP 3:** Handle repositories with complex relationships:
- `RoundRepositoryTest` (FK to Room, relationship to Votes)
- `RoomParticipantRepositoryTest` (FK to Room + User, nullable user_id)

**STEP 4:** Complete the composite key entities:
- `OrgMemberRepositoryTest` (composite key: org_id + user_id)

**STEP 5:** Finish with partitioned tables:
- `SessionHistoryRepositoryTest` (partitioned by started_at, JSONB fields)
- `AuditLogRepositoryTest` (partitioned by timestamp, composite key, nullable FKs)

**ESTIMATED TIME:** 30-45 minutes per repository test = 4.5-6.75 hours total for 9 repositories

### Example Test Template

Here's the EXACT structure you must follow for each new repository test:

```java
package com.scrumpoker.repository;

import com.scrumpoker.domain.xxx.Xxx;
import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for XxxRepository.
 * Tests CRUD operations, custom finders, [JSONB fields / relationships / soft delete] using Testcontainers PostgreSQL.
 */
@QuarkusTest
class XxxRepositoryTest {

    @Inject
    XxxRepository xxxRepository;

    // Inject dependent repositories for foreign keys
    @Inject
    UserRepository userRepository;

    private Xxx testEntity;
    private User testUser;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up in reverse dependency order (children first, then parents)
        asserter.execute(() -> Panache.withTransaction(() -> xxxRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));

        // Create test entities (but don't persist in setUp - each test persists as needed)
        testUser = createTestUser("user@example.com", "google", "google-123");
        testEntity = createTestEntity();
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: prerequisite entities
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(testUser)));

        // Given: a new entity
        Xxx entity = createTestEntity();

        // When: persisting the entity
        asserter.execute(() -> Panache.withTransaction(() -> xxxRepository.persist(entity)));

        // Then: the entity can be retrieved by ID
        asserter.assertThat(() -> Panache.withTransaction(() -> xxxRepository.findById(entity.id)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.field).isEqualTo("expectedValue");
            assertThat(found.createdAt).isNotNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testCustomFinderMethod(UniAsserter asserter) {
        // Given: persisted test data
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(testUser)));
        asserter.execute(() -> Panache.withTransaction(() -> xxxRepository.persist(testEntity)));

        // When: using custom finder
        // Then: correct entity is found
        asserter.assertThat(() -> Panache.withTransaction(() -> xxxRepository.findByXxx("criteria")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.id).isEqualTo(testEntity.id);
        });
    }

    /**
     * Helper method to create test users.
     */
    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    /**
     * Helper method to create test entities.
     */
    private Xxx createTestEntity() {
        Xxx entity = new Xxx();
        entity.id = UUID.randomUUID(); // or explicit String ID for Room-like entities
        entity.field = "value";
        entity.user = testUser; // Set FK reference
        return entity;
    }
}
```

---

## 4. Final Reminders

**CRITICAL REQUIREMENTS:**
1. **Follow UserRepositoryTest pattern EXACTLY** - it's the gold standard reference
2. **Test ALL custom finder methods** defined in each repository interface
3. **Test JSONB round-trip** for entities with JSONB fields (UserPreference, Organization, SessionHistory, Room, AuditLog)
4. **Test soft delete behavior** for User and Room entities
5. **Test relationship navigation** for all foreign key relationships
6. **Clean up in BeforeEach** to ensure test isolation
7. **Use AssertJ assertions** consistently throughout
8. **Achieve >80% test coverage** for repository classes
9. **Run `mvn test` frequently** to verify tests pass as you implement them
10. **Study the 3 existing test files** (UserRepositoryTest, RoomRepositoryTest, VoteRepositoryTest) before writing new tests

**SUCCESS DEFINITION:**
- All 12 repository test classes exist and have comprehensive tests
- `mvn test` executes successfully with all tests passing
- Testcontainers starts PostgreSQL automatically
- Test coverage report shows >80% for repository package
- No flaky tests (consistent results across multiple runs)

Good luck! 🚀
