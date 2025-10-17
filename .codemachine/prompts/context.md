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

### Context: Testcontainers (from 03_Verification_and_Glossary.md)

```markdown
| **Testcontainers** | Java library providing lightweight, disposable database/cache containers for integration tests |
```

### Context: Uni and Multi (from 03_Verification_and_Glossary.md)

```markdown
| **Uni** | Mutiny type representing asynchronous single-item result (similar to CompletableFuture) |
| **Multi** | Mutiny type representing asynchronous stream of 0-N items (similar to Reactive Streams Publisher) |
```

### Context: Panache (from 03_Verification_and_Glossary.md)

```markdown
| **Panache** | Quarkus extension simplifying Hibernate with active record or repository patterns |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** This file contains a comprehensive example of reactive repository testing using Quarkus Test with UniAsserter. It demonstrates the correct pattern for testing all repository operations including persist, findById, custom finders, soft deletes, and updates.
    *   **Recommendation:** You MUST follow this exact testing pattern for all remaining repository tests. This file is your golden template - use the same structure, annotations, and assertion patterns.
    *   **Critical Pattern:** All async operations use `@RunOnVertxContext` and `UniAsserter`. Each test uses `asserter.execute()` for operations and `asserter.assertThat()` for assertions. All database operations MUST be wrapped in `Panache.withTransaction()`.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** This file demonstrates testing complex entity hierarchies with foreign key relationships. It shows how to properly persist parent entities before children (User → Room → Round → Participant → Vote) and how to test relationship navigation.
    *   **Recommendation:** When testing repositories with foreign key dependencies (like RoomParticipant, Round, SessionHistory), you MUST use this hierarchical persistence pattern. Always persist parent entities first using chained `.flatMap()` operations.
    *   **Critical Pattern:** Helper methods create entities without IDs (IDs are auto-generated by Hibernate). Timestamps like `votedAt` are set manually for test data control. The file shows proper cleanup order (children deleted before parents in `@BeforeEach`).

*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** This repository implements `PanacheRepositoryBase<User, UUID>` with custom finder methods returning `Uni<>` types. It demonstrates the standard pattern for reactive repositories with methods like `findByEmail()`, `findByOAuthProviderAndSubject()`, and `countActive()`.
    *   **Recommendation:** All 12 repository interfaces follow this same pattern. Each custom finder method you test MUST verify it returns the correct reactive type (Uni or Multi) and handles null results properly.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
    *   **Summary:** This repository shows special handling for Room entities which use String primary keys (6-character nanoid) instead of UUID. It implements `PanacheRepositoryBase<Room, String>`.
    *   **Recommendation:** When testing RoomRepository, you MUST manually set the `roomId` field (e.g., "vote01") since it's NOT auto-generated. Also note that Room has soft delete functionality via `deletedAt` timestamp.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** Room entity contains a JSONB `config` field stored as a String. Timestamps `createdAt` and `lastActiveAt` use `@CreationTimestamp` and `@UpdateTimestamp` annotations. The entity supports soft deletes with `deletedAt` field.
    *   **Recommendation:** Your Room tests MUST verify JSONB serialization by setting `room.config = "{\"deckType\":\"fibonacci\"}"` and verifying it persists and retrieves correctly. Test soft delete by setting `deletedAt = Instant.now()` and verifying the entity is excluded from `findActive*` queries.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
    *   **Summary:** UserPreference has multiple JSONB columns: `defaultRoomConfig` and `notificationSettings`. It uses `@MapsId` annotation for one-to-one relationship with User, meaning the userId is the primary key.
    *   **Recommendation:** UserPreference tests MUST test the one-to-one relationship mapping. When testing JSONB fields, verify both null and populated JSON strings persist correctly.

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Test configuration file shows that Testcontainers is enabled by NOT setting explicit database URLs. Flyway migrations run automatically with `quarkus.flyway.migrate-at-start=true`.
    *   **Recommendation:** You do NOT need to modify this file. Testcontainers will automatically start PostgreSQL when tests run. The existing configuration is correct.

*   **File:** `backend/pom.xml`
    *   **Summary:** Maven configuration includes all necessary test dependencies: `quarkus-junit5`, `quarkus-test-vertx` (for UniAsserter), `rest-assured`, and `assertj-core`. Surefire plugin is configured for unit tests.
    *   **Recommendation:** All test dependencies are already configured. You do NOT need to add any new dependencies.

### Implementation Tips & Notes

*   **Tip #1 - Reactive Testing Pattern:** EVERY test method MUST use `@RunOnVertxContext` and accept `UniAsserter asserter` as a parameter. Use `asserter.execute()` for operations that don't return values you need to assert on, and `asserter.assertThat()` for operations where you need to verify the result.

*   **Tip #2 - Transaction Boundaries:** ALL database operations in tests MUST be wrapped in `Panache.withTransaction(() -> ...)`. This is critical for reactive repositories - without it, your tests will fail with transaction errors.

*   **Tip #3 - Entity Hierarchy Persistence:** When testing entities with foreign keys, you MUST persist parent entities BEFORE child entities. Use chained `.flatMap()` operations to ensure proper ordering. For example: `userRepository.persist(user).flatMap(u -> roomRepository.persist(room).flatMap(r -> roundRepository.persist(round)))`.

*   **Tip #4 - Test Isolation:** The `@BeforeEach` setup method MUST call `deleteAll()` on ALL repositories in reverse dependency order (children first, then parents) to ensure test isolation. This prevents data pollution between tests.

*   **Tip #5 - JSONB Testing:** For JSONB fields like `Room.config`, set them as JSON strings: `room.config = "{\"deckType\":\"fibonacci\",\"timerEnabled\":true}"`. Verify the string persists exactly as stored - PostgreSQL JSONB may reformat whitespace but content should match.

*   **Tip #6 - Soft Delete Testing:** For User and Room entities, test soft delete by setting `deletedAt = Instant.now()`, persisting, then verifying that `findActive*()` methods exclude the soft-deleted entity while `findById()` still finds it.

*   **Tip #7 - AssertJ Assertions:** Use AssertJ's fluent assertions for readability: `assertThat(found).isNotNull()`, `assertThat(found.email).isEqualTo("test@example.com")`, `assertThat(list).hasSize(3)`, `assertThat(list).extracting(v -> v.cardValue).containsExactly("3", "5", "8")`.

*   **Tip #8 - Repository Count:** You need to create tests for 12 repositories total. Based on the file tree, these are: `UserRepository`, `UserPreferenceRepository`, `RoomRepository`, `RoomParticipantRepository`, `RoundRepository`, `VoteRepository`, `SessionHistoryRepository`, `OrganizationRepository`, `OrgMemberRepository`, `SubscriptionRepository`, `PaymentHistoryRepository`, `AuditLogRepository`. Three already exist (UserRepository, VoteRepository partially complete based on file listing), so you need to verify/complete the remaining 9+ test files.

*   **Note #1 - Entity ID Generation:** Most entities use UUID primary keys that are AUTO-GENERATED by Hibernate. Do NOT set the ID field when creating test entities (except for Room which uses manual String IDs). The ID will be populated after `persist()` completes.

*   **Note #2 - Timestamp Fields:** Entities with `@CreationTimestamp` and `@UpdateTimestamp` will have timestamps auto-set by Hibernate. However, for test data control (like vote ordering), you may manually set `votedAt` fields AFTER creating the entity but BEFORE persisting.

*   **Note #3 - Composite Keys:** OrgMember and potentially SessionHistory use composite primary keys (defined by `@Id` annotations on multiple fields or separate ID classes). Test these carefully - persistence and findById will use the composite key class.

*   **Note #4 - Coverage Target:** The acceptance criteria states >80% test coverage for repository classes. Since repositories are thin wrappers around Panache, focusing on thorough testing of custom finder methods and JSONB/soft delete behavior will easily achieve this target.

*   **Warning #1 - Async Pitfall:** Do NOT use standard JUnit assertions inside reactive chains. If you try `assertEquals()` inside a `.map()` or `.flatMap()`, the assertion may not execute or fail silently. Always use `asserter.assertThat()` at the outer level to properly chain assertions.

*   **Warning #2 - Test Ordering:** Tests must NOT depend on execution order. Each test should be fully independent. The `@BeforeEach` cleanup ensures this, but verify your tests don't assume data from previous tests exists.

*   **Warning #3 - Testcontainers Startup:** The first test run may take 30-60 seconds as Testcontainers downloads the PostgreSQL Docker image. Subsequent runs will be faster. This is normal and expected behavior.

*   **Best Practice:** Create helper methods at the bottom of each test class (like `createTestUser()`, `createTestRoom()`) that construct entity instances with valid default values. This makes tests more readable and maintainable. See VoteRepositoryTest for excellent examples.

---

**END OF TASK BRIEFING PACKAGE**

Good luck, Coder Agent! You have all the patterns, examples, and context you need to complete these repository integration tests successfully. Follow the existing patterns closely, and you'll achieve >80% coverage with comprehensive, reliable tests.
