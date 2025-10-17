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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** This is a comprehensive reference implementation for repository integration tests using the REACTIVE pattern with `@RunOnVertxContext` and `UniAsserter`. It contains 11 test methods covering CRUD operations, custom finders (findByEmail, findByOAuthProviderAndSubject, findActiveByEmail), soft delete behavior, and count operations.
    *   **Recommendation:** This file demonstrates the CORRECT reactive pattern for Quarkus Panache testing. However, note that RoomRepositoryTest uses a SIMPLER blocking pattern with `@Transactional`. You SHOULD use the blocking pattern for simplicity unless you specifically want reactive testing.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** This test uses the BLOCKING pattern with `@Transactional` annotation and `.await().indefinitely()` on all reactive operations. It contains 14 test methods covering String ID persistence, JSONB config fields, relationship navigation to User and Organization, multiple custom finders, soft delete filtering, and update operations.
    *   **Recommendation:** You SHOULD use this as your primary template. The `@Transactional` + `.await().indefinitely()` pattern is MUCH simpler than the reactive UniAsserter pattern and is perfectly acceptable for integration tests. This is the recommended approach for remaining tests.

*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** This file EXISTS but has CRITICAL BUGS. It attempts to use reactive pattern with `@RunOnVertxContext` but has scoping errors where it references `testRound` and `testParticipant` that are not properly initialized. Many test methods fail to compile because they try to use these undefined variables.
    *   **Recommendation:** You MUST COMPLETELY FIX OR REWRITE this test file. The main issues are: (1) Missing proper test data setup - all entity creation should be in `@BeforeEach` or within each test method, (2) Complex entity hierarchy not properly managed (User → Room → Round AND User → Room → RoomParticipant must be created and persisted before Vote).

*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** Reactive Panache repository with 6 custom finder methods for vote queries.
    *   **Recommendation:** Your fixed VoteRepositoryTest MUST cover ALL 6 methods:
        1. `findByRoundId(UUID)` - All votes in a round, ordered by votedAt
        2. `findByRoomIdAndRoundNumber(String, Integer)` - Alternative round query using room ID
        3. `findByParticipantId(UUID)` - All votes by a participant
        4. `findByRoundIdAndParticipantId(UUID, UUID)` - Specific participant's vote in a round
        5. `countByRoundId(UUID)` - Count of votes in a round
        6. `findByRoundIdAndCardValue(UUID, String)` - Votes with specific card value in a round

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
    *   **Summary:** Vote entity with UUID primary key, ManyToOne relationships to Round and RoomParticipant, unique constraint on (round_id, participant_id), cardValue field supporting special characters (?, ∞, ☕).
    *   **Recommendation:** Your test MUST verify: (1) Unique constraint enforcement - cannot insert duplicate vote for same participant+round, (2) Special character card values persist correctly, (3) Relationship navigation works (`vote.round` and `vote.participant` accessible), (4) `votedAt` timestamp auto-set on creation.

*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Already correctly configured for Testcontainers. Database URLs are intentionally left unset to trigger Dev Services automatic PostgreSQL container. Flyway migrations run at startup (`quarkus.flyway.migrate-at-start=true`).
    *   **Recommendation:** NO CHANGES NEEDED. The task mentions `application-test.properties` but that file is NOT needed - the existing `application.properties` in test resources is correct. Testcontainers works automatically with this configuration.

### Implementation Tips & Notes

*   **Tip:** Use the BLOCKING pattern (`@Transactional` + `.await().indefinitely()`) from RoomRepositoryTest for all remaining tests. It's simpler and easier to debug than the reactive UniAsserter pattern. Example structure:
    ```java
    @BeforeEach
    @Transactional
    void setUp() {
        voteRepository.deleteAll().await().indefinitely();
        // delete other repos as needed
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        Vote vote = createTestVote();
        voteRepository.persist(vote).await().indefinitely();

        Vote found = voteRepository.findById(vote.voteId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.cardValue).isEqualTo("5");
    }
    ```

*   **Note:** VoteRepositoryTest requires complex entity hierarchy setup. Before testing Vote, you MUST create and persist: User → Room → Round AND User → Room → RoomParticipant. The proper pattern is:
    ```java
    User user = createTestUser("voter@example.com", "google", "google-123");
    userRepository.persist(user).await().indefinitely();

    Room room = createTestRoom("room01", "Test Room", user);
    roomRepository.persist(room).await().indefinitely();

    Round round = createTestRound(room, 1, "Test Story");
    roundRepository.persist(round).await().indefinitely();

    RoomParticipant participant = createTestParticipant(room, user, "Alice");
    participantRepository.persist(participant).await().indefinitely();

    Vote vote = createTestVote(round, participant, "5");
    voteRepository.persist(vote).await().indefinitely();
    ```

*   **Warning:** The existing VoteRepositoryTest.java has CRITICAL SCOPING BUGS. Many test methods try to use `testRound` and `testParticipant` variables that don't exist in proper scope. You MUST either: (1) Create these as instance fields and initialize them in `@BeforeEach`, OR (2) Create all test data within each test method. I recommend option 1 for consistency with other tests.

*   **Tip:** For JSONB field testing (Room.config, UserPreference.default_room_config, Organization.sso_config), the pattern is simple string comparison:
    ```java
    String jsonConfig = "{\"deckType\":\"fibonacci\",\"timerEnabled\":true}";
    room.config = jsonConfig;
    roomRepository.persist(room).await().indefinitely();

    Room found = roomRepository.findById(room.roomId).await().indefinitely();
    assertThat(found.config).isEqualTo(jsonConfig);
    ```

*   **Note:** Soft delete testing pattern (from UserRepositoryTest): Set `deletedAt = Instant.now()`, persist, verify entity still exists with deletedAt set, then verify custom "active" finders exclude the soft-deleted entity.

*   **Tip:** Use AssertJ fluent assertions for collections:
    ```java
    assertThat(votes).hasSize(3);
    assertThat(votes).extracting(v -> v.cardValue).containsExactlyInAnyOrder("3", "5", "8");
    assertThat(votes).allMatch(v -> v.round.roundId.equals(testRound.roundId));
    ```

*   **Critical:** You MUST inject ALL required repositories for VoteRepositoryTest:
    ```java
    @Inject VoteRepository voteRepository;
    @Inject RoundRepository roundRepository;
    @Inject RoomParticipantRepository participantRepository;
    @Inject RoomRepository roomRepository;
    @Inject UserRepository userRepository;
    ```

*   **Tip:** Test cleanup in `@BeforeEach` should delete in reverse dependency order:
    ```java
    voteRepository.deleteAll().await().indefinitely();
    roundRepository.deleteAll().await().indefinitely();
    participantRepository.deleteAll().await().indefinitely();
    roomRepository.deleteAll().await().indefinitely();
    userRepository.deleteAll().await().indefinitely();
    ```

*   **Note:** The unique constraint on Vote (round_id, participant_id) means you CANNOT insert two votes for the same participant in the same round. To test this constraint, you can either: (1) Expect an exception when attempting duplicate, OR (2) Simply avoid creating duplicates in your test data setup.

*   **Tip:** For testing special character card values (?, ∞, ☕), create separate test method that persists these values and verifies they round-trip correctly through PostgreSQL:
    ```java
    Vote questionVote = createTestVote(testRound, participant1, "?");
    Vote infinityVote = createTestVote(testRound, participant2, "∞");
    Vote coffeeVote = createTestVote(testRound, participant3, "☕");
    ```

*   **Warning:** Always use `.await().indefinitely()` on ALL reactive operations when using the blocking pattern. Forgetting this will cause NullPointerException or incomplete data in tests.

*   **Critical:** The task mentions 12 repository test classes, but only 3 exist currently (UserRepositoryTest, RoomRepositoryTest, VoteRepositoryTest - though VoteRepositoryTest needs major fixes). The remaining 9 repositories need tests created from scratch. However, for THIS specific task (I1.T8), you should focus on FIXING VoteRepositoryTest since UserRepositoryTest and RoomRepositoryTest are already complete and passing.
