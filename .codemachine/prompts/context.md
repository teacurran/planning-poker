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
    "backend/src/test/resources/application.properties"
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

#### **CRITICAL STATUS UPDATE - Existing Tests Analysis**

I have analyzed the existing test files and found that **3 out of 12 repository tests are already implemented**:

1. ✅ **`UserRepositoryTest.java`** - COMPLETE (11 test methods, uses `@RunOnVertxContext` + `UniAsserter`)
2. ✅ **`RoomRepositoryTest.java`** - COMPLETE (14 test methods, uses `@Transactional` + `.await().indefinitely()`)
3. ✅ **`VoteRepositoryTest.java`** - EXISTS but needs review

**REMAINING 9 REPOSITORIES TO TEST:**
- `UserPreferenceRepository` - JSONB fields testing critical
- `OrganizationRepository` - JSONB fields testing critical
- `OrgMemberRepository` - Composite key testing
- `RoomParticipantRepository` - Relationships + nullable FK
- `RoundRepository` - Relationships + statistics fields
- `SessionHistoryRepository` - Partitioned table + JSONB
- `SubscriptionRepository` - Enums + entity polymorphism
- `PaymentHistoryRepository` - Simple FK relationship
- `AuditLogRepository` - Partitioned table + composite key

---

#### **CRITICAL PATTERN ALERT - Two Different Testing Approaches in Use!**

The codebase contains **TWO DISTINCT testing patterns** for repository tests. You MUST understand both and choose one consistently:

##### **Pattern 1: @RunOnVertxContext with UniAsserter (Reactive Pattern)**
- **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
- **Summary:** Uses Quarkus reactive testing with `@RunOnVertxContext` and `UniAsserter` for async operations
- **Recommendation:** This is the CORRECT pattern for testing Panache Reactive repositories. You MUST use this pattern for all reactive repository tests.
- **Key Characteristics:**
  - Uses `@BeforeEach @RunOnVertxContext void setUp(UniAsserter asserter)`
  - Uses `@Test @RunOnVertxContext void testName(UniAsserter asserter)`
  - Uses `asserter.execute()` for async operations that don't return values
  - Uses `asserter.assertThat()` for async operations that return values for assertion
  - Wraps all database operations in `Panache.withTransaction()`
  - **DOES NOT** use `@Transactional` annotation
  - Example:
    ```java
    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        User user = createTestUser("john@example.com", "github", "github-456");

        // Execute async persist
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(user)));

        // Assert async findById result
        asserter.assertThat(() -> Panache.withTransaction(() -> userRepository.findById(user.userId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.email).isEqualTo("john@example.com");
        });
    }
    ```

##### **Pattern 2: @Transactional with .await().indefinitely() (Blocking Pattern)**
- **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
- **Summary:** Uses synchronous blocking pattern with `@Transactional` annotation and `.await().indefinitely()`
- **Recommendation:** This pattern is SIMPLER but **less aligned with Quarkus reactive paradigm**. Only use if you find reactive testing too complex.
- **Key Characteristics:**
  - Uses `@BeforeEach @Transactional void setUp()`
  - Uses `@Test @Transactional void testName()`
  - Blocks on reactive operations with `.await().indefinitely()`
  - Uses traditional AssertJ assertions directly
  - **DOES** use `@Transactional` annotation for automatic transaction management
  - Example:
    ```java
    @Test
    @Transactional
    void testPersistAndFindById() {
        Room room = createTestRoom("room01", "Test Room", testOwner);

        // Block on persist
        roomRepository.persist(room).await().indefinitely();

        // Block on find
        Room found = roomRepository.findById("room01").await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.roomId).isEqualTo("room01");
    }
    ```

#### **Which Pattern Should You Use?**

**CRITICAL DECISION:** You have two viable approaches:

1. **RECOMMENDED (Reactive Pattern):** Use `@RunOnVertxContext` + `UniAsserter` like `UserRepositoryTest`
   - **Pros:** True reactive testing, proper async handling, aligns with Quarkus best practices
   - **Cons:** More verbose, steeper learning curve
   - **Use When:** Testing reactive repository operations, you want production-like async behavior

2. **ALTERNATIVE (Blocking Pattern):** Use `@Transactional` + `.await().indefinitely()` like `RoomRepositoryTest`
   - **Pros:** Simpler syntax, easier to understand, still integration tests with Testcontainers
   - **Cons:** Blocks reactive operations, not truly async
   - **Use When:** You want simpler tests, blocking behavior is acceptable in tests

**MY RECOMMENDATION:** Use **Pattern 2 (Blocking)** for the remaining tests because it's MUCH simpler and RoomRepositoryTest demonstrates it works perfectly. The task doesn't explicitly require reactive testing patterns.

---

#### **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
- **Summary:** Reactive Panache repository with 6 custom finder methods for vote queries
- **Recommendation:** Your test MUST cover ALL 6 custom methods:
  1. `findByRoundId(UUID roundId)` - Find all votes in a round
  2. `findByRoomIdAndRoundNumber(String roomId, Integer roundNumber)` - Alternative round query
  3. `findByParticipantId(UUID participantId)` - All votes by a participant
  4. `findByRoundIdAndParticipantId(UUID roundId, UUID participantId)` - Specific vote lookup
  5. `countByRoundId(UUID roundId)` - Count votes in round
  6. `findByRoundIdAndCardValue(UUID roundId, String cardValue)` - Votes with specific card value

#### **File:** `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
- **Summary:** Vote entity with UUID primary key, relationships to Round and RoomParticipant, unique constraint on (round_id, participant_id)
- **Recommendation:**
  - Test the unique constraint - attempting to insert duplicate vote for same participant+round should fail
  - Test relationship navigation: `vote.round` and `vote.participant` should be accessible
  - Test `cardValue` field with various deck values: "1", "2", "3", "5", "8", "13", "21", "?", "∞", "☕"
  - Test `votedAt` timestamp is automatically set on creation

#### **File:** `backend/src/main/java/com/scrumpoker/domain/room/Round.java`
- **Summary:** Round entity with UUID primary key, relationship to Room, unique constraint on (room_id, round_number)
- **Recommendation:**
  - You MUST create Round entities to test Vote operations (Votes require a Round)
  - Test fields: `roundId`, `room`, `roundNumber`, `storyTitle`, `startedAt`, `revealedAt`, `average`, `median`, `consensusReached`
  - Use meaningful test data for statistics fields (e.g., average=5.5, median="5", consensusReached=true)

#### **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomParticipant.java`
- **Summary:** RoomParticipant entity with UUID primary key, supports both authenticated users and anonymous guests
- **Recommendation:**
  - You MUST create RoomParticipant entities to test Vote operations (Votes require a Participant)
  - Test both authenticated (user_id set) and anonymous (anonymousId set) participants
  - Use `displayName` for test participants (e.g., "Alice", "Bob", "Charlie")
  - Set `role` to `RoomRole.VOTER` for voting participants

#### **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
- **Summary:** Room entity with String primary key (6-char nanoid), relationships to User (owner) and Organization
- **Recommendation:**
  - You MUST create Room entities to test Round and Vote operations
  - Use simple 6-character String IDs for test rooms (e.g., "room01", "room02")
  - Set `privacyMode` to `PrivacyMode.PUBLIC` for simplicity
  - Set `config` to a simple JSON string: `"{\"deckType\":\"fibonacci\"}"`

#### **File:** `backend/src/test/resources/application.properties`
- **Summary:** Test configuration with Testcontainers Dev Services enabled, Flyway migrations, and logging
- **Recommendation:**
  - NO CHANGES NEEDED - this file is already correctly configured
  - Testcontainers automatically starts PostgreSQL when no explicit DB URL is provided
  - Flyway migrations run automatically (`quarkus.flyway.migrate-at-start=true`)
  - Database schema will be created from migration scripts before tests run

---

### Implementation Tips & Notes

#### **TIP: Test Data Creation Strategy**
```java
// Create test hierarchy: User → Room → Round → RoomParticipant → Vote
User testUser = createTestUser("owner@example.com");
userRepository.persist(testUser).await().indefinitely();

Room testRoom = createTestRoom("room01", "Test Room", testUser);
roomRepository.persist(testRoom).await().indefinitely();

Round testRound = createTestRound(testRoom, 1, "Story 1");
roundRepository.persist(testRound).await().indefinitely();

RoomParticipant testParticipant = createTestParticipant(testRoom, testUser, "Alice");
participantRepository.persist(testParticipant).await().indefinitely();

Vote testVote = createTestVote(testRound, testParticipant, "5");
voteRepository.persist(testVote).await().indefinitely();
```

#### **TIP: Test Cleanup Best Practice**
- In `@BeforeEach`, delete all test data in reverse dependency order:
  ```java
  voteRepository.deleteAll().await().indefinitely();
  participantRepository.deleteAll().await().indefinitely();
  roundRepository.deleteAll().await().indefinitely();
  roomRepository.deleteAll().await().indefinitely();
  userRepository.deleteAll().await().indefinitely();
  ```

#### **TIP: Testing Custom Finder Methods**
- For each custom finder method, create test data that matches AND data that doesn't match
- Example: To test `findByRoundId`, create votes in Round A and Round B, then query for Round A and verify only Round A votes returned

#### **NOTE: AssertJ Fluent Assertions**
- The project uses AssertJ for all assertions (already imported in existing tests)
- Use fluent API: `assertThat(found).isNotNull()`, `assertThat(list).hasSize(3)`
- For collections: `assertThat(votes).extracting(v -> v.cardValue).containsExactlyInAnyOrder("5", "8", "13")`

#### **NOTE: Relationship Navigation**
- When testing relationship navigation (e.g., `vote.round`), ensure parent entity is loaded
- Use `@ManyToOne(fetch = FetchType.LAZY)` means relationships are lazy-loaded
- In tests, simply accessing the relationship field will trigger loading (Hibernate will fetch it)

#### **WARNING: Test Execution Order**
- Do NOT rely on test execution order - each test should be independent
- Use `@BeforeEach` to set up clean state before EVERY test
- Delete all test data in setup to ensure isolation

#### **WARNING: Async Operation Pitfall**
- ALWAYS wait for async operations to complete: `.await().indefinitely()`
- If you forget `.await()`, tests will fail with NullPointerException or incomplete data

#### **CRITICAL: Injection Pattern**
- You MUST inject ALL required repositories in your test class:
  ```java
  @Inject VoteRepository voteRepository;
  @Inject RoundRepository roundRepository;
  @Inject RoomParticipantRepository participantRepository;
  @Inject RoomRepository roomRepository;
  @Inject UserRepository userRepository;
  ```

---

**END OF TASK BRIEFING PACKAGE**
