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
  "inputs": "*   Repository interfaces from I1.T7\n        *   Testcontainers setup patterns for PostgreSQL\n        *   Sample entity instances for testing",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   12 repository test classes with minimum 3 test methods each (create, findById, custom finder)\n        *   Testcontainers PostgreSQL configuration in test profile\n        *   Tests for JSONB field operations (Room.config, UserPreference.default_room_config)\n        *   Soft delete tests verifying `deleted_at` timestamp behavior\n        *   Foreign key relationship tests (e.g., deleting User cascades to UserPreference)",
  "acceptance_criteria": "*   `mvn test` executes all repository tests successfully\n        *   Testcontainers starts PostgreSQL container automatically\n        *   All CRUD operations pass (insert, select, update, delete)\n        *   Custom finder methods return expected results\n        *   JSONB fields round-trip correctly (save and retrieve complex objects)\n        *   Soft delete tests confirm `deleted_at` set correctly\n        *   Test coverage >80% for repository classes",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: task-i1-t8 (from 02_Iteration_I1.md)

```markdown
*   **Task 1.8: Write Integration Tests for Repositories**
    *   **Task ID:** `I1.T8`
    *   **Description:** Create integration tests for all Panache repositories using Testcontainers (PostgreSQL container). Write tests for: entity persistence (insert, update, delete), custom finder methods, relationship navigation, JSONB field serialization/deserialization, soft delete behavior (User, Room). Use Quarkus `@QuarkusTest` annotation with `@TestProfile` for test database configuration. Assert results using AssertJ or Rest Assured for fluent assertions.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Repository interfaces from I1.T7
        *   Testcontainers setup patterns for PostgreSQL
        *   Sample entity instances for testing
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/repository/*.java` (all repository files)
        *   `backend/src/main/java/com/scrumpoker/domain/**/*.java` (entity files)
    *   **Target Files:**
        *   `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
        *   `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
        *   `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
        *   (... test files for each of 12 repositories)
        *   `backend/src/test/resources/application-test.properties`
    *   **Deliverables:**
        *   12 repository test classes with minimum 3 test methods each (create, findById, custom finder)
        *   Testcontainers PostgreSQL configuration in test profile
        *   Tests for JSONB field operations (Room.config, UserPreference.default_room_config)
        *   Soft delete tests verifying `deleted_at` timestamp behavior
        *   Foreign key relationship tests (e.g., deleting User cascades to UserPreference)
    *   **Acceptance Criteria:**
        *   `mvn test` executes all repository tests successfully
        *   Testcontainers starts PostgreSQL container automatically
        *   All CRUD operations pass (insert, select, update, delete)
        *   Custom finder methods return expected results
        *   JSONB fields round-trip correctly (save and retrieve complex objects)
        *   Soft delete tests confirm `deleted_at` set correctly
        *   Test coverage >80% for repository classes
    *   **Dependencies:** [I1.T7]
    *   **Parallelizable:** No (depends on repository implementation)
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

#### Entity Relationship Diagram (PlantUML)

~~~plantuml
@startuml

' User and Authentication
entity User {
  *user_id : UUID <<PK>>
  --
  email : VARCHAR(255) <<UNIQUE>>
  oauth_provider : VARCHAR(50)
  oauth_subject : VARCHAR(255)
  display_name : VARCHAR(100)
  avatar_url : VARCHAR(500)
  subscription_tier : ENUM(FREE, PRO)
  created_at : TIMESTAMP
  deleted_at : TIMESTAMP
}

entity UserPreference {
  *user_id : UUID <<PK, FK>>
  --
  default_deck_type : VARCHAR(50)
  default_room_config : JSONB
  theme : VARCHAR(20)
  notification_settings : JSONB
}

' Organization and Membership
entity Organization {
  *org_id : UUID <<PK>>
  --
  name : VARCHAR(200)
  domain : VARCHAR(100)
  sso_config : JSONB
  branding : JSONB
  subscription_id : UUID <<FK>>
  created_at : TIMESTAMP
}

entity OrgMember {
  *org_id : UUID <<PK, FK>>
  *user_id : UUID <<PK, FK>>
  --
  role : ENUM(ADMIN, MEMBER)
  joined_at : TIMESTAMP
}

' Room and Session
entity Room {
  *room_id : VARCHAR(6) <<PK>>
  --
  owner_id : UUID <<FK>> nullable
  org_id : UUID <<FK>> nullable
  title : VARCHAR(200)
  privacy_mode : ENUM(PUBLIC, INVITE_ONLY, ORG_RESTRICTED)
  config : JSONB
  created_at : TIMESTAMP
  last_active_at : TIMESTAMP
  deleted_at : TIMESTAMP
}

entity RoomParticipant {
  *participant_id : UUID <<PK>>
  --
  room_id : VARCHAR(6) <<FK>>
  user_id : UUID <<FK>> nullable
  anonymous_id : VARCHAR(50)
  display_name : VARCHAR(100)
  role : ENUM(HOST, VOTER, OBSERVER)
  connected_at : TIMESTAMP
  disconnected_at : TIMESTAMP
}

entity Round {
  *round_id : UUID <<PK>>
  --
  room_id : VARCHAR(6) <<FK>>
  round_number : INTEGER
~~~
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/test/java/com/scrumpoker/repository/UserRepositoryTest.java`
    *   **Summary:** Repository integration coverage for users already exists with `@QuarkusTest`, `@RunOnVertxContext`, and `UniAsserter`. It seeds data via helper `createTestUser`, persists through `Panache.withTransaction`, and exercises CRUD operations, finder variants (`findByEmail`, `findByOAuthProviderAndSubject`, `findActiveByEmail`), counters, and soft deletes.
    *   **Recommendation:** Mirror this structure for other repositories—wrap every assert or mutation in `Panache.withTransaction`, reuse helper builders to avoid duplicate setup, and assert on key domain fields such as `deletedAt`, `subscriptionTier`, and `displayName` to prove mappings.
*   **File:** `backend/src/test/java/com/scrumpoker/repository/RoomRepositoryTest.java`
    *   **Summary:** Demonstrates how to test multi-entity interactions: rooms use string IDs, rely on JSONB `config` strings, and relate to both `User` (owner) and `Organization`. Tests cover relationship navigation, filtering, counting, and manual timestamp manipulation to bypass `@UpdateTimestamp`.
    *   **Recommendation:** When testing repositories with relationships (e.g., `RoomParticipantRepository`, `RoundRepository`), persist parents first (user → room → round) exactly as shown to satisfy FK constraints, and include JSONB round-trip assertions for configurable columns.
*   **File:** `backend/src/test/java/com/scrumpoker/repository/VoteRepositoryTest.java`
    *   **Summary:** Exercises a complex hierarchy (User → Room → Round → RoomParticipant → Vote) and shows how to chain persistence calls reactively. Coverage includes relationship navigation, list finders, counts, and verifying ordering semantics.
    *   **Recommendation:** Use this file as the blueprint for other repositories that require deep graph setup (SessionHistory, AuditLog). Pay attention to the cleanup order in `@BeforeEach` (children first) to prevent FK violations and to the use of explicit timestamps when ordering should be deterministic.
*   **File:** `backend/src/test/resources/application.properties`
    *   **Summary:** Configures Quarkus tests to rely on Dev Services/Testcontainers (no explicit datasource URLs), disables security layers during repository tests, enables Flyway migrations, and tweaks logging plus Redis/Testcontainers behavior.
    *   **Recommendation:** Keep this profile untouched; when adding new tests ensure they run under the default test config (no custom `@TestProfile` needed). If you require Redis or other services, Dev Services will bootstrap them as long as you avoid overriding hosts/ports in this file.

### Implementation Tips & Notes
*   **Tip:** Always annotate integration tests with both `@QuarkusTest` and `@RunOnVertxContext`; use `UniAsserter` to coordinate asynchronous operations and wrap repository calls in `Panache.withTransaction` to guarantee DB access happens within a reactive transaction.
*   **Tip:** Helper factory methods should avoid manually assigning auto-generated IDs (UUIDs) and instead set only business fields; for `Room` string IDs, set the 6-character key explicitly as shown.
*   **Tip:** Clean up tables in child-to-parent order inside `@BeforeEach` using `repository.deleteAll()` so subsequent tests start with a pristine state without violating foreign keys.
*   **Tip:** For JSONB columns (Room.config, UserPreference.defaultRoomConfig, Organization.ssoConfig), persist actual JSON strings and assert on key fragments to verify serialization.
*   **Note:** Soft-delete behavior is validated by setting `deletedAt = Instant.now()` and confirming finder methods that target “active” rows exclude the record; include similar checks wherever the domain uses soft deletes.
*   **Note:** The project relies on Quarkus Dev Services to spin up PostgreSQL and Redis automatically—do not hardcode JDBC URLs or Redis hosts in tests, or Dev Services will not activate.
*   **Warning:** Repository tests execute concurrently on Vert.x event loops; avoid blocking calls (`Thread.sleep`, synchronous waits) and prefer Mutiny constructs so the suite remains stable under CI load.
