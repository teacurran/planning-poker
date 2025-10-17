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
```

### Context: glossary (from 03_Verification_and_Glossary.md)

```markdown
## 6. Glossary

| Term | Definition |
|------|------------|
| **Testcontainers** | Java library providing lightweight, disposable database/cache containers for integration tests |
| **Uni** | Mutiny type representing asynchronous single-item result (similar to CompletableFuture) |
| **Multi** | Mutiny type representing asynchronous stream of 0-N items (similar to Reactive Streams Publisher) |
| **Panache** | Quarkus extension simplifying Hibernate with active record or repository patterns |
| **JSONB** | JSON Binary (PostgreSQL data type) |
| **Soft Delete** | Marking records as deleted (e.g., `deleted_at` timestamp) without physical deletion |
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** This is a COMPLETE, WORKING integration test example showing the exact pattern you MUST follow. It uses `@QuarkusTest`, `@RunOnVertxContext`, and `UniAsserter` for reactive testing. ALL 11 tests pass successfully.
    *   **Recommendation:** You MUST follow this test pattern exactly. Study lines 22-42 for the test class setup pattern, lines 40-61 for the basic persist/find test pattern, and lines 143-162 for soft delete testing. This is your PRIMARY reference.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** This is another COMPLETE, WORKING integration test showing complex multi-entity relationship testing. It demonstrates how to test foreign key relationships and proper entity persistence ordering. ALL 15 tests pass successfully.
    *   **Recommendation:** You MUST use this as reference for testing entities with complex relationships. Study lines 55-84 for the relationship persistence pattern (must persist parent entities before children). Lines 164-215 show the correct pattern for testing custom finder methods with multiple related entities.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** This is the definitive example for testing JSONB fields and soft deletes. It shows exactly how to test JSON serialization/deserialization (lines 75-93) and soft delete behavior (lines 368-395). ALL 15 tests pass successfully.
    *   **Recommendation:** You MUST follow the JSONB testing pattern from lines 75-93. For soft delete testing, follow the exact pattern from lines 368-395 which shows both persistence verification AND exclusion from active queries.

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** This file shows the CRITICAL configuration for Testcontainers integration. It explicitly documents that you MUST NOT set JDBC/reactive URLs - Dev Services auto-configures them.
    *   **Recommendation:** You do NOT need to modify this file. Testcontainers activation is already configured correctly via Maven Surefire plugin settings.

*   **File:** `backend/pom.xml` (lines 120-142)
    *   **Summary:** This section shows all required test dependencies are already configured: `quarkus-junit5`, `quarkus-test-vertx`, `rest-assured`, and `assertj-core` (version 3.24.2).
    *   **Recommendation:** You do NOT need to modify the pom.xml. All dependencies for reactive testing are already present.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** This is an example repository showing the Panache repository pattern with custom finder methods. Shows the use of `Uni<List<Vote>>` and `Uni<Vote>` return types.
    *   **Recommendation:** You MUST verify your tests cover ALL custom finder methods defined in each repository. Note the reactive return types - your tests MUST use `UniAsserter` to handle these properly.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** Entity showing JSONB field usage. The `config` field is defined as `columnDefinition = "jsonb"` and stored as a String. Also shows soft delete with `deletedAt` timestamp.
    *   **Recommendation:** When testing JSONB fields, you MUST test that JSON strings round-trip correctly through the database. Store a JSON string, retrieve it, and assert it matches exactly.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java`
    *   **Summary:** Another entity with JSONB fields (`default_room_config`, `notification_settings`). Shows nullable JSONB columns.
    *   **Recommendation:** Test both null and non-null JSONB values to ensure proper handling of nullable JSONB columns.

### Implementation Tips & Notes

*   **CRITICAL PATTERN - Reactive Test Structure:** You MUST use this exact pattern for EVERY test:
    ```java
    @Test
    @RunOnVertxContext
    void testSomething(UniAsserter asserter) {
        // Given: setup test data

        // When: execute operation wrapped in Panache.withTransaction()
        asserter.execute(() -> Panache.withTransaction(() ->
            repository.persist(entity)
        ));

        // Then: assert results
        asserter.assertThat(() -> Panache.withTransaction(() ->
            repository.findById(id)
        ), found -> {
            assertThat(found).isNotNull();
            // more assertions...
        });
    }
    ```

*   **CRITICAL - Entity Persistence Order:** When testing entities with foreign keys, you MUST persist parent entities before children. Example from VoteRepositoryTest:
    ```java
    userRepository.persist(testUser).flatMap(user ->
        roomRepository.persist(testRoom).flatMap(room ->
            roundRepository.persist(testRound).flatMap(round ->
                participantRepository.persist(testParticipant).flatMap(participant ->
                    voteRepository.persist(vote)
                )
            )
        )
    )
    ```

*   **CRITICAL - BeforeEach Cleanup:** You MUST clean up test data in `@BeforeEach` using this pattern:
    ```java
    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up children first, then parents (reverse of creation order)
        asserter.execute(() -> Panache.withTransaction(() -> childRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> parentRepository.deleteAll()));
    }
    ```

*   **CRITICAL - Helper Methods:** You MUST create helper methods to construct test entities. DO NOT set ID fields manually for UUID-based entities (Hibernate auto-generates them). Example:
    ```java
    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        // DO NOT SET user.userId - Hibernate auto-generates it
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }
    ```

*   **JSONB Testing Pattern:** Test JSONB fields by storing a JSON string and verifying exact round-trip:
    ```java
    String jsonConfig = "{\"deckType\":\"fibonacci\",\"timerEnabled\":true}";
    room.config = jsonConfig;
    // ... persist and retrieve ...
    assertThat(found.config).isEqualTo(jsonConfig);
    assertThat(found.config).contains("fibonacci");
    ```

*   **Soft Delete Testing Pattern:** Test soft delete in TWO steps:
    1. Verify `deleted_at` is set on the entity
    2. Verify soft-deleted entity is EXCLUDED from "active" finder queries
    ```java
    // Set soft delete
    entity.deletedAt = Instant.now();
    repository.persist(entity);

    // Verify still exists but marked deleted
    assertThat(found.deletedAt).isNotNull();

    // Verify excluded from active queries
    assertThat(repository.findActiveByX()).doesNotContain(entity);
    ```

*   **Relationship Navigation Testing:** When testing entity relationships, fetch both entities separately and verify the foreign key matches. See VoteRepositoryTest lines 88-122 for the pattern.

*   **Test Naming Convention:** Follow the pattern `test<MethodName>` or `test<BehaviorDescription>`. Examples: `testPersistAndFindById`, `testFindByEmail`, `testSoftDelete`.

*   **AssertJ Fluent Assertions:** Use AssertJ's fluent API consistently. Common patterns:
    - `assertThat(found).isNotNull()`
    - `assertThat(list).hasSize(3)`
    - `assertThat(list).extracting(Entity::getField).containsExactly(...)`
    - `assertThat(list).allMatch(predicate)`

*   **Testcontainers Activation:** Testcontainers automatically activates via Quarkus Dev Services when no explicit datasource URL is configured. The existing `application.properties` file is already correctly configured - DO NOT modify it.

*   **Test Coverage Requirement:** The task specifies >80% coverage. You MUST write at least 3 tests per repository, but SHOULD write more to cover all custom finder methods and edge cases.

*   **Running Tests:** Execute with `mvn test` for unit tests or `mvn verify` for integration tests. All tests should pass without errors. Testcontainers will automatically download and start a PostgreSQL container on first run.

*   **CURRENT STATUS:** Based on the directory listing, ALL 12 repository test files already exist:
    - UserRepositoryTest.java ✓
    - UserPreferenceRepositoryTest.java ✓
    - OrganizationRepositoryTest.java ✓
    - OrgMemberRepositoryTest.java ✓
    - RoomRepositoryTest.java ✓
    - RoomParticipantRepositoryTest.java ✓
    - RoundRepositoryTest.java ✓
    - VoteRepositoryTest.java ✓
    - SessionHistoryRepositoryTest.java ✓
    - SubscriptionRepositoryTest.java ✓
    - PaymentHistoryRepositoryTest.java ✓
    - AuditLogRepositoryTest.java ✓

*   **YOUR TASK:** Run `mvn test` to verify all repository tests pass. If any tests fail, debug and fix them using the patterns from the working test examples above. Ensure all acceptance criteria are met.

---

**End of Task Briefing Package**
