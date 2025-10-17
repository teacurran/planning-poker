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

The data model follows a relational schema leveraging PostgreSQL's ACID properties for transactional consistency and JSONB columns for flexible configuration storage (room settings, deck definitions). The model is optimized for both transactional writes (vote casting, room creation) and analytical reads (session history, organizational reporting).

**Design Principles:**
1. **Normalized Core Entities:** Users, Rooms, Organizations follow 3NF to prevent update anomalies
2. **Denormalized Read Models:** SessionSummary and VoteStatistics tables precompute aggregations for reporting performance
3. **JSONB for Flexibility:** RoomConfig, DeckDefinition, UserPreferences stored as JSONB to support customization without schema migrations
4. **Soft Deletes:** Critical entities (Users, Rooms) use `deleted_at` timestamp for audit trail and GDPR compliance
5. **Partitioning Strategy:** SessionHistory and AuditLog partitioned by month for query performance and data lifecycle management

**Key Entities:**

| Entity | Purpose | Key Attributes |
|--------|---------|----------------|
| **User** | Registered user account | `user_id` (PK), `email`, `oauth_provider`, `oauth_subject`, `display_name`, `avatar_url`, `subscription_tier`, `created_at`, `deleted_at` (soft delete) |
| **UserPreference** | Saved user defaults | `user_id` (FK), `default_deck_type`, `default_room_config` (JSONB), `theme`, `notification_settings` (JSONB) |
| **Organization** | Enterprise SSO workspace | `org_id` (PK), `name`, `domain`, `sso_config` (JSONB: OIDC/SAML2 settings), `branding` (JSONB), `subscription_id` (FK) |
| **OrgMember** | User-organization membership | `org_id` (FK), `user_id` (FK), `role` (ADMIN/MEMBER), `joined_at` |
| **Room** | Estimation session | `room_id` (PK, nanoid 6-char), `owner_id` (FK nullable for anonymous), `org_id` (FK nullable), `title`, `privacy_mode` (PUBLIC/INVITE_ONLY/ORG_RESTRICTED), `config` (JSONB: deck, rules, timer), `created_at`, `last_active_at`, `deleted_at` (soft delete) |
| **RoomParticipant** | Active session participants | `room_id` (FK), `user_id` (FK nullable), `anonymous_id`, `display_name`, `role` (HOST/VOTER/OBSERVER), `connected_at` |
| **Vote** | Individual estimation vote | `vote_id` (PK), `room_id` (FK), `round_number`, `participant_id`, `card_value`, `voted_at` |
| **Round** | Estimation round within session | `round_id` (PK), `room_id` (FK), `round_number`, `story_title`, `started_at`, `revealed_at`, `average`, `median`, `consensus_reached` |
| **SessionHistory** | Completed session record | `session_id` (PK), `room_id` (FK), `started_at`, `ended_at`, `total_rounds`, `total_stories`, `participants` (JSONB array), `summary_stats` (JSONB) |
| **Subscription** | Stripe subscription record | `subscription_id` (PK), `stripe_subscription_id`, `entity_id` (user_id or org_id), `entity_type` (USER/ORG), `tier` (FREE/PRO/PRO_PLUS/ENTERPRISE), `status`, `current_period_end`, `canceled_at` |
| **PaymentHistory** | Payment transaction log | `payment_id` (PK), `subscription_id` (FK), `stripe_invoice_id`, `amount`, `currency`, `status`, `paid_at` |
| **AuditLog** | Compliance and security audit trail | `log_id` (PK), `org_id` (FK nullable), `user_id` (FK nullable), `action`, `resource_type`, `resource_id`, `ip_address`, `user_agent`, `timestamp` |

**Database Indexing Strategy:**

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

### Context: integration-testing (from 03_Verification_and_Glossary.md)

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

### Context: unit-testing (from 03_Verification_and_Glossary.md)

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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** This is a Reactive Panache repository implementing `PanacheRepositoryBase<User, UUID>`. It provides custom finder methods like `findByEmail()`, `findByOAuthProviderAndSubject()`, `findActiveByEmail()`, and `countActive()`. All methods return reactive types (`Uni<User>`, `Uni<Long>`).
    *   **Recommendation:** You MUST create a test class `UserRepositoryTest.java` that tests all these custom finder methods. Pay special attention to testing the soft delete behavior with `findActiveByEmail()` and `countActive()` methods, which filter by `deletedAt IS NULL`.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
    *   **Summary:** This repository uses **String as the primary key** (6-character nanoid), NOT UUID. It implements `PanacheRepositoryBase<Room, String>`. It has rich custom finders: `findActiveByOwnerId()`, `findByOrgId()`, `findPublicRooms()`, `findByPrivacyMode()`, `findInactiveSince()`, `countActiveByOwnerId()`, and `countByOrgId()`.
    *   **Recommendation:** CRITICAL - Your tests MUST use **String IDs (6-character nanoids)** for Room entities, not UUIDs. Test the soft delete filtering (`deletedAt IS NULL` in all active queries). Test relationship navigation to `owner` (User) and `organization`.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** Repository for Vote entity with UUID primary key. Provides methods for finding votes by round ID, room ID + round number, participant ID, and specific round + participant combinations. Includes count methods and a finder for votes with specific card values.
    *   **Recommendation:** Test the relationship navigation to `round` and `participant` entities. Test the ordering by `votedAt`. The method `findByRoomIdAndRoundNumber()` uses a nested relationship path (`round.room.roomId`) - ensure this query works correctly.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** Entity extending `PanacheEntityBase` with UUID primary key. Uses `@Cacheable`, has soft delete via `deletedAt`, unique constraints on email and oauth provider+subject. Uses `@CreationTimestamp` and `@UpdateTimestamp` for automatic timestamp management.
    *   **Recommendation:** When testing User persistence, you do NOT need to manually set `createdAt` or `updatedAt` - Hibernate will auto-populate these. Test that `deletedAt` starts as NULL and gets set when you perform a soft delete. Test unique constraint violations (duplicate email should throw exception).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** Entity with **String primary key** (`room_id`, 6 characters). Has `config` field as JSONB stored as String (`columnDefinition = "jsonb"`). Has ManyToOne relationships to User (owner) and Organization. Uses soft delete via `deletedAt`.
    *   **Recommendation:** CRITICAL - Room uses `String` for `roomId`, not UUID. You MUST manually generate unique 6-character IDs for test rooms. Test JSONB serialization/deserialization by setting `config` to a JSON string and verifying it persists correctly. Test lazy-loaded relationships (`owner`, `organization`) by accessing them after persist.

*   **File:** `backend/src/test/resources/application-test.properties`
    *   **Summary:** Test configuration file that currently has hardcoded database URLs for localhost. It enables Flyway migrations at start (`quarkus.flyway.migrate-at-start=true`) and disables OIDC for tests.
    *   **Recommendation:** You MUST update this file to use Testcontainers-provided database URLs. Quarkus has built-in Testcontainers support - you should configure it to use `%test` profile with Testcontainers. The file currently points to localhost, which won't work in CI or on developer machines without manually running PostgreSQL.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven POM with Quarkus 3.15.1, Java 17, includes quarkus-junit5 and rest-assured test dependencies. Has Surefire and Failsafe plugins configured.
    *   **Recommendation:** CRITICAL - The POM is **MISSING Testcontainers dependency**. You MUST add the following dependency to enable Testcontainers support:
      ```xml
      <dependency>
          <groupId>io.quarkus</groupId>
          <artifactId>quarkus-test-container</artifactId>
          <scope>test</scope>
      </dependency>
      ```
      Or alternatively, use the standard Testcontainers PostgreSQL module:
      ```xml
      <dependency>
          <groupId>org.testcontainers</groupId>
          <artifactId>postgresql</artifactId>
          <scope>test</scope>
      </dependency>
      ```

### Implementation Tips & Notes

*   **Tip:** I discovered that all repository methods return reactive types (`Uni<>` or `Multi<>`). In your tests, you MUST call `.await().indefinitely()` to block and get the actual result. For example: `User user = userRepository.findById(userId).await().indefinitely();`

*   **Tip:** Quarkus has excellent Testcontainers integration. You can use the `@QuarkusTest` annotation with a custom test profile. Create a test profile class annotated with `@TestProfile` that configures Testcontainers. Quarkus will automatically start/stop containers for each test class.

*   **Tip:** For JSONB testing (Room.config, UserPreference.default_room_config, Organization.sso_config), these fields are stored as **String** in the Java entities. You should set them to valid JSON strings (e.g., `"{\"deckType\":\"fibonacci\",\"timerEnabled\":true}"`) and verify they persist and can be retrieved.

*   **Note:** The project uses Flyway migrations. The test configuration has `quarkus.flyway.migrate-at-start=true`, which means your database schema will be automatically created from the migration scripts in `backend/src/main/resources/db/migration/` when tests run. You don't need to manually create tables.

*   **Note:** Soft deletes are implemented via `deleted_at` timestamp fields on User and Room entities. When testing soft deletes, you should set `deletedAt = Instant.now()` and verify that "active" finder methods (like `findActiveByEmail()`) exclude those entities.

*   **Warning:** The Room entity uses a **String primary key** (6-character nanoid), NOT UUID. Many other entities use UUID. Be careful not to mix up ID types in your tests. You'll need a utility to generate test nanoids (or just use hardcoded 6-char strings like "test01", "room01", etc. for simplicity).

*   **Warning:** Several repositories have relationship navigation in queries (e.g., `round.room.roomId`, `owner.userId`, `participant.participantId`). These use JPA path expressions. Make sure related entities are persisted BEFORE the entity that references them. For example, persist a User before persisting a Room that has that User as owner.

*   **Tip:** Use AssertJ for fluent assertions as recommended in the task description. Import `org.assertj.core.api.Assertions.*` and use methods like `assertThat(user).isNotNull()`, `assertThat(user.email).isEqualTo("test@example.com")`.

*   **Tip:** For testing relationship cascades (e.g., "deleting User cascades to UserPreference"), you'll need to check the Flyway migration scripts to understand which foreign keys have `ON DELETE CASCADE` configured. Based on the ERD, UserPreference should cascade delete when User is deleted.

*   **Note:** The test directory currently only has `application-test.properties` - there are NO existing test classes. You are creating the first test classes for this project. Follow standard Maven test conventions: test classes should be in `src/test/java` mirroring the production package structure.

*   **Tip:** You should create 12 test classes (one for each repository). However, to meet the task acceptance criteria efficiently, prioritize creating thorough tests for UserRepository, RoomRepository, and VoteRepository (which are listed in target_files), and create basic tests for the other 9 repositories to ensure >80% coverage overall.

*   **CRITICAL:** Quarkus 3.x has changed the way Testcontainers integration works. Instead of manually configuring Testcontainers, you should add the `quarkus-test-container` dependency and use the `%test` profile configuration. Quarkus will automatically detect PostgreSQL and start a container. Update `application-test.properties` to remove hardcoded URLs and use: `quarkus.datasource.devservices.enabled=true` in the test profile.

*   **Best Practice:** Each test method should be isolated. Use `@BeforeEach` to ensure a clean database state, or use Quarkus transaction rollback features. Consider using `@Transactional` on test methods so changes are automatically rolled back after each test.

*   **Best Practice:** For integration tests that verify CRUD operations, follow this pattern:
    1. CREATE: Persist entity using repository
    2. READ: Retrieve using `findById()` to verify it was saved
    3. UPDATE: Modify entity and persist again
    4. READ: Retrieve again to verify update
    5. DELETE: Call delete (or soft delete)
    6. READ: Verify entity is gone (or has `deletedAt` set)
