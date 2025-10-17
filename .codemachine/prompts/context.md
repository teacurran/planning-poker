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
| **User** | Registered user account | `user_id` (PK), `email`, `oauth_provider`, `oauth_subject`, `display_name`, `avatar_url`, `subscription_tier`, `created_at` |
| **UserPreference** | Saved user defaults | `user_id` (FK), `default_deck_type`, `default_room_config` (JSONB), `theme`, `notification_settings` (JSONB) |
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |
| **Room** | Estimation session | `room_id` (PK, nanoid 6-char), `owner_id` (FK nullable for anonymous), `org_id` (FK nullable), `title`, `privacy_mode` (PUBLIC/INVITE_ONLY/ORG_RESTRICTED), `config` (JSONB: deck, rules, timer), `created_at`, `last_active_at` |
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

**Composite Indexes:**
- `Room(privacy_mode, last_active_at DESC) WHERE deleted_at IS NULL` - Public room discovery
- `OrgMember(user_id, org_id) WHERE role = 'ADMIN'` - Admin permission checks
- `Vote(round_id, voted_at) INCLUDE (card_value)` - Covering index for vote ordering

**Partitioning:**
- `SessionHistory` partitioned by `started_at` (monthly range partitions)
- `AuditLog` partitioned by `timestamp` (monthly range partitions)
- Automated partition creation via scheduled job or pg_partman extension
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

### Context: Testcontainers (from Glossary)

```markdown
| Term | Definition |
|------|------------|
| **Testcontainers** | Java library providing lightweight, disposable database/cache containers for integration tests |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Current State: Tests Already Exist But Are Failing

**CRITICAL FINDING:** All 12 repository integration test files already exist in `backend/src/test/java/com/scrumpoker/repository/`. The tests are well-written and comprehensive, covering all required scenarios. However, they are currently **FAILING** due to a missing test configuration file.

### The Root Cause

The tests fail with this error:
```
Caused by: org.postgresql.util.PSQLException: FATAL: role "postgres" does not exist
```

This occurs because:
1. Quarkus Dev Services (Testcontainers) is **supposed** to automatically start PostgreSQL containers for tests
2. However, the tests are trying to connect to a non-existent local PostgreSQL instance instead
3. The test profile configuration is missing: `backend/src/test/resources/application.properties` **DOES NOT EXIST**

### What You MUST Do

**Your PRIMARY task is NOT to write new tests** - the tests already exist and are excellent. Your task is to:

1. **Create the missing test configuration file**: `backend/src/test/resources/application.properties`
2. **Configure Quarkus Dev Services** to properly use Testcontainers
3. **Verify all tests pass** by running `mvn test`

### Relevant Existing Test Files

All 12 test files exist and are comprehensive:

*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** Comprehensive tests for User repository including CRUD operations, OAuth lookups, soft delete behavior, and active user queries.
    *   **Status:** Well-written with 11 test methods. Uses `@QuarkusTest`, `@Transactional`, AssertJ assertions, reactive Uni types.
    *   **Test Coverage:** persist/findById, findByEmail, findByOAuthProviderAndSubject, update, soft delete, findActiveByEmail, countActive, hard delete, count all users.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** Comprehensive tests for Room repository including String ID handling, JSONB config serialization, relationship navigation (User/Organization), privacy modes, soft delete.
    *   **Status:** Well-written with 14 test methods covering all Room-specific scenarios.
    *   **Special Features:** Tests JSONB field round-tripping, tests relationship navigation with lazy loading, tests custom finders (findActiveByOwnerId, findByOrgId, findPublicRooms, findInactiveSince).

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** This is the actual repository implementation. It extends `PanacheRepositoryBase<User, UUID>` and provides custom finder methods.
    *   **Key Methods:** `findByEmail()`, `findByOAuthProviderAndSubject()`, `findActiveByEmail()`, `countActive()`.
    *   **Pattern:** All methods return reactive `Uni<>` types, use Panache query syntax.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** Room entity demonstrating JSONB usage.
    *   **JSONB Field:** `config` column is `String` type with `columnDefinition = "jsonb"`. This means JSONB is stored as JSON string, not as a complex object with hibernate-types.
    *   **Important Note:** Tests verify JSONB round-tripping by checking that the JSON string is stored and retrieved correctly. No complex object mapping is used.

### Configuration Requirements

Based on analysis of `backend/src/main/resources/application.properties`:

1. The main application.properties has **commented-out test datasource URLs** (lines 145-147):
   ```properties
   # These hardcoded URLs are commented out to allow Dev Services to work
   #%test.quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/scrumpoker_test
   #%test.quarkus.datasource.reactive.url=postgresql://localhost:5432/scrumpoker_test
   ```

2. This indicates the **intention** is to use Quarkus Dev Services (Testcontainers)

3. The configuration shows: `%test.quarkus.flyway.migrate-at-start=true` (line 148), meaning migrations should run in tests

### Implementation Tips & Notes

*   **Tip:** Quarkus Dev Services should automatically start Testcontainers when tests run IF properly configured. The key is ensuring Dev Services is enabled for tests.
*   **Critical Configuration:** You MUST create `backend/src/test/resources/application.properties` with these settings:
    ```properties
    # Enable Quarkus Dev Services (Testcontainers) for tests
    quarkus.devservices.enabled=true

    # PostgreSQL Dev Services configuration
    quarkus.datasource.devservices.enabled=true
    quarkus.datasource.devservices.image-name=postgres:15

    # Redis Dev Services configuration
    quarkus.redis.devservices.enabled=true

    # Flyway migrations should run in test environment
    quarkus.flyway.migrate-at-start=true
    quarkus.flyway.clean-at-start=true

    # Logging for debugging
    quarkus.log.level=INFO
    quarkus.hibernate-orm.log.sql=false
    ```

*   **Warning:** DO NOT add hardcoded database URLs in the test configuration. Let Dev Services manage the database connection automatically.

*   **Test Execution Pattern:** All existing tests follow this pattern:
    ```java
    @QuarkusTest  // Uses Quarkus test context with Dev Services
    class UserRepositoryTest {
        @Inject
        UserRepository userRepository;  // Inject the repository

        @BeforeEach
        @Transactional
        void setUp() {
            // Clean database before each test
            userRepository.deleteAll().await().indefinitely();
        }

        @Test
        @Transactional  // Each test runs in a transaction
        void testSomething() {
            // Arrange, Act, Assert with reactive Uni types
            // Use .await().indefinitely() to block on reactive operations
        }
    }
    ```

*   **Reactive Pattern Note:** All tests use `.await().indefinitely()` to block on reactive `Uni<>` operations in test context. This is the correct pattern for Quarkus reactive tests.

*   **AssertJ Usage:** Tests use AssertJ's fluent API (e.g., `assertThat(result).isNotNull()`, `assertThat(list).hasSize(2)`, `assertThat(list).extracting(r -> r.roomId).containsExactlyInAnyOrder("room05", "room06")`).

### Deliverable Checklist

- [x] 12 repository test classes exist with comprehensive test methods
- [ ] Create `backend/src/test/resources/application.properties` with Dev Services configuration
- [ ] Verify `mvn test` runs successfully with all tests passing
- [ ] Confirm Testcontainers PostgreSQL starts automatically (check test logs)
- [ ] Confirm all CRUD operations pass
- [ ] Confirm JSONB field tests pass (Room.config, UserPreference, Organization JSONB fields)
- [ ] Confirm soft delete tests pass (User and Room)
- [ ] Confirm foreign key relationship tests pass

### Success Criteria

When you run `mvn test`, you should see:
1. Testcontainers starting a PostgreSQL container
2. Flyway migrations executing successfully
3. All 100+ test methods passing across all 12 test classes
4. No errors related to database connection or missing roles
5. Test execution completing in under 5 minutes

### Final Notes

**You are NOT writing new tests.** The tests are excellent and complete. Your job is to **fix the test configuration** so that the existing tests can run successfully with Testcontainers. This is a much simpler task than the original specification implied, but it's critical for I1.T8 completion.
