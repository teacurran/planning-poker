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

### Context: unit-testing (from 03_Verification_and_Glossary.md)

```markdown
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

### Context: Testcontainers (from glossary)

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
    *   **Summary:** This is a COMPLETE and WORKING integration test for UserRepository. It demonstrates the EXACT patterns you MUST follow for all repository tests.
    *   **Recommendation:** You MUST study this file carefully. It shows:
        - How to use `@QuarkusTest` annotation for integration tests
        - How to use `@RunOnVertxContext` and `UniAsserter` for reactive testing with Hibernate Reactive Panache
        - How to clean up test data in `@BeforeEach` using `Panache.withTransaction(() -> repository.deleteAll())`
        - How to persist entities reactively using `Panache.withTransaction(() -> repository.persist(entity))`
        - How to assert on reactive results using `asserter.assertThat(() -> Panache.withTransaction(() -> repository.findById(id)), found -> { ... })`
        - How to test soft delete behavior by setting `deletedAt` timestamp
        - How to create helper methods for creating test entities
    *   **Critical Pattern:** ALL repository operations in tests MUST be wrapped in `Panache.withTransaction(() -> ...)` to ensure transaction boundaries

*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** This is a COMPLETE and WORKING integration test for RoomRepository demonstrating more complex scenarios including JSONB field testing and relationship navigation.
    *   **Recommendation:** This file shows CRITICAL patterns for:
        - Testing JSONB field serialization/deserialization (Room.config field with JSON string)
        - Testing relationship navigation (Room → Owner, Room → Organization)
        - Testing custom finder methods (`findActiveByOwnerId`, `findByOrgId`, `findPublicRooms`, `findByPrivacyMode`)
        - Testing soft delete behavior with verification that soft-deleted entities are excluded from active queries
        - Using HQL mutation queries to update timestamps (for testing inactive rooms)
        - Setting timestamps manually on entities (`room.createdAt = Instant.now()`) when `@CreationTimestamp` annotation won't work in test setup
    *   **Critical Pattern:** When persisting entities with relationships, you MUST persist the parent entities FIRST using nested `flatMap` chains: `userRepository.persist(user).flatMap(u -> roomRepository.persist(room))`

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** This is a COMPLETE and WORKING integration test for VoteRepository demonstrating complex entity hierarchies and relationship testing.
    *   **Recommendation:** This file demonstrates the MOST COMPLEX test scenarios you'll encounter:
        - Persisting deep entity hierarchies (User → Room → Round → Participant → Vote)
        - Using deeply nested `flatMap` chains to establish correct persistence order
        - Testing ordering of query results (votes ordered by `votedAt`)
        - Testing special character values (?, ∞, ☕) to ensure VARCHAR columns handle Unicode
        - Capturing auto-generated IDs from persisted entities using holder arrays: `final UUID[] voteIdHolder = new UUID[1]`
        - Testing delete operations by fetching entity first within transaction, then deleting
    *   **Critical Pattern:** For entities with multiple levels of foreign key relationships, you MUST persist in dependency order: parent → child → grandchild, using nested `flatMap` chains

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
    *   **Summary:** Example entity class showing JPA annotations, relationships, and validation constraints.
    *   **Recommendation:** You SHOULD understand entity structure when writing tests:
        - `@GeneratedValue(strategy = GenerationType.AUTO)` means IDs are auto-generated - DO NOT set them manually in test helpers
        - `@ManyToOne(fetch = FetchType.LAZY)` means relationships are lazy-loaded
        - `@NotNull` and `@Size` constraints are validated by Hibernate
        - Entities extend `PanacheEntityBase` for Panache repository pattern

*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** Example repository interface showing custom finder methods using Panache query syntax.
    *   **Recommendation:** You MUST test ALL custom finder methods defined in each repository:
        - Methods like `findByRoundId()`, `findByParticipantId()`, `countByRoundId()` must have corresponding test methods
        - Test both success cases (data found) and failure cases (data not found, returns empty list or null)
        - Verify that ordering clauses work correctly (e.g., `order by votedAt`)

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Test configuration file for Quarkus tests. This file is ALREADY COMPLETE and correctly configured.
    *   **Recommendation:** You SHOULD NOT modify this file. It is already correctly configured:
        - Empty datasource URLs allow Quarkus Dev Services (Testcontainers) to automatically start PostgreSQL
        - Flyway migrations enabled (`quarkus.flyway.migrate-at-start=true`) to apply schema migrations
        - OIDC disabled for tests (`quarkus.oidc.enabled=false`)
        - Hibernate set to NOT auto-generate schema (`quarkus.hibernate-orm.database.generation=none`) because Flyway handles migrations
    *   **Note:** The task mentions creating `application-test.properties` in target_files, but the existing `application.properties` is the correct file to use for tests. You do NOT need to create a separate `-test.properties` file.

### Implementation Tips & Notes

*   **Tip:** ALL 12 repository test files MUST follow the EXACT pattern demonstrated in `UserRepositoryTest.java`, `RoomRepositoryTest.java`, and `VoteRepositoryTest.java`. These are your templates.

*   **Tip:** When creating test helper methods (e.g., `createTestUser()`, `createTestRoom()`), you MUST NOT set the primary key ID field (e.g., `userId`, `roomId` for UUID fields) because it is auto-generated by Hibernate. The ONLY exception is `Room.roomId` which is a manually-assigned 6-character String, not auto-generated.

*   **Tip:** For entities with `@CreationTimestamp` or `@UpdateTimestamp` annotations, you SHOULD set these timestamps MANUALLY in test helpers (e.g., `room.createdAt = Instant.now()`) because these annotations may not trigger during test entity creation.

*   **Tip:** When testing soft delete behavior, you MUST:
    1. Persist the entity normally
    2. Update the entity to set `deletedAt = Instant.now()`
    3. Verify the entity still exists when retrieved by ID directly
    4. Verify the entity is EXCLUDED from "active" queries (e.g., `findActiveByEmail()`)

*   **Tip:** For JSONB field testing, you SHOULD:
    1. Set the JSONB field to a valid JSON string (e.g., `"{\"key\":\"value\"}"`)
    2. Persist the entity
    3. Retrieve the entity and assert the JSONB field contains the expected JSON structure
    4. Use `assertThat(config).contains("key")` to verify specific JSON content

*   **Tip:** For relationship navigation testing, you MUST:
    1. Persist both parent and child entities
    2. Retrieve the child entity
    3. Assert the relationship field is not null
    4. Optionally fetch the parent separately and compare IDs to verify relationship integrity

*   **Warning:** Testcontainers will automatically start a PostgreSQL container when tests run. This is configured by Quarkus Dev Services and happens automatically when no explicit datasource URL is provided. You do NOT need to configure Testcontainers explicitly.

*   **Warning:** When testing entities with foreign key relationships, you MUST persist entities in dependency order (parent before child) to avoid foreign key constraint violations. Use nested `flatMap` chains to ensure correct ordering.

*   **Warning:** The `UniAsserter` from `io.quarkus.test.vertx.UniAsserter` is the CORRECT way to test reactive code in Quarkus. DO NOT use `Uni.await()` or blocking operations in tests.

*   **Critical Pattern:** Every test method that performs database operations MUST follow this structure:
    ```java
    @Test
    @RunOnVertxContext
    void testSomething(UniAsserter asserter) {
        // Setup (usually in @BeforeEach, but can be here)
        Entity entity = createTestEntity();

        // Execute: wrap in Panache.withTransaction
        asserter.execute(() -> Panache.withTransaction(() ->
            repository.persist(entity)
        ));

        // Assert: wrap in Panache.withTransaction and use asserter.assertThat
        asserter.assertThat(() -> Panache.withTransaction(() ->
            repository.findById(entity.id)
        ), found -> {
            assertThat(found).isNotNull();
            assertThat(found.someField).isEqualTo(expectedValue);
        });
    }
    ```

*   **Coverage Target:** Each repository test class MUST have a MINIMUM of 3 test methods, but you SHOULD aim for comprehensive coverage:
    - At least 1 test for basic CRUD (persist, findById)
    - At least 1 test for EACH custom finder method
    - At least 1 test for update operations
    - At least 1 test for delete operations (soft delete if applicable)
    - At least 1 test for relationship navigation (if entity has relationships)
    - At least 1 test for JSONB fields (if entity has JSONB columns)
    - At least 1 test for count methods

*   **Execution Command:** Run tests using `mvn test` (unit tests) or `mvn verify` (integration tests). The task acceptance criteria requires `mvn test` to pass, which runs both unit and integration tests marked with `@QuarkusTest`.

*   **Current Status:** Based on the directory listing, I can see that the following repository test files ALREADY EXIST and are likely complete:
    - `UserRepositoryTest.java` ✓ (verified complete)
    - `RoomRepositoryTest.java` ✓ (verified complete)
    - `VoteRepositoryTest.java` ✓ (verified complete)
    - `UserPreferenceRepositoryTest.java` ✓
    - `OrganizationRepositoryTest.java` ✓
    - `OrgMemberRepositoryTest.java` ✓
    - `RoomParticipantRepositoryTest.java` ✓
    - `RoundRepositoryTest.java` ✓
    - `SessionHistoryRepositoryTest.java` ✓
    - `SubscriptionRepositoryTest.java` ✓
    - `PaymentHistoryRepositoryTest.java` ✓
    - `AuditLogRepositoryTest.java` ✓

*   **ACTION REQUIRED:** You MUST verify that all 12 repository test files are complete and pass all acceptance criteria. Run `mvn test` to confirm all tests pass. If any tests fail, debug and fix them. If any test classes are incomplete (missing test methods), add the missing tests following the patterns from the working examples.

*   **Quality Check:** After completing this task, you MUST run `mvn test` and ensure:
    1. All tests pass (green checkmarks)
    2. No test failures or errors
    3. Testcontainers successfully starts PostgreSQL
    4. Flyway migrations execute successfully
    5. No test pollution (each test can run independently)
    6. Test coverage meets >80% target for repository classes (verify with JaCoCo report)

---

**End of Briefing Package**

You now have all the information needed to complete Task I1.T8. Focus on verifying and completing the repository integration tests, ensuring they follow the established patterns and meet all acceptance criteria.
