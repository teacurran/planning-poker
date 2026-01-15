# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T7",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Implement Panache repository interfaces for all entities using `PanacheRepositoryBase` pattern. Create repositories: `UserRepository`, `UserPreferenceRepository`, `OrganizationRepository`, `OrgMemberRepository`, `RoomRepository`, `RoomParticipantRepository`, `RoundRepository`, `VoteRepository`, `SessionHistoryRepository`, `SubscriptionRepository`, `PaymentHistoryRepository`, `AuditLogRepository`. Add custom finder methods (e.g., `UserRepository.findByEmail()`, `RoomRepository.findActiveByOwnerId()`, `VoteRepository.findByRoundId()`). Use reactive return types (`Uni<>`, `Multi<>`).",
  "agent_type_hint": "BackendAgent",
  "inputs": "*   Entity classes from I1.T4\n        *   Common query patterns from architecture blueprint (e.g., user lookup by email, rooms by owner)\n        *   Panache repository patterns from Quarkus docs",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   12 Panache repository classes implementing `PanacheRepositoryBase<Entity, UUID>`\n        *   Custom finder methods with reactive return types (`Uni<User>`, `Multi<Room>`)\n        *   Query methods using Panache query syntax (e.g., `find(\"email\", email).firstResult()`)\n        *   ApplicationScoped CDI beans for dependency injection",
  "acceptance_criteria": "*   Maven compilation successful\n        *   Repositories injectable via `@Inject` in service classes\n        *   Custom finder methods return correct reactive types\n        *   Query methods execute without errors against seeded database\n        *   Integration test for each repository demonstrates CRUD operations work",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: task-i1-t7 (from 02_Iteration_I1.md)

```markdown
*   **Task 1.7: Create Panache Repository Interfaces**
    *   **Task ID:** `I1.T7`
    *   **Description:** Implement Panache repository interfaces for all entities using `PanacheRepositoryBase` pattern. Create repositories: `UserRepository`, `UserPreferenceRepository`, `OrganizationRepository`, `OrgMemberRepository`, `RoomRepository`, `RoomParticipantRepository`, `RoundRepository`, `VoteRepository`, `SessionHistoryRepository`, `SubscriptionRepository`, `PaymentHistoryRepository`, `AuditLogRepository`. Add custom finder methods (e.g., `UserRepository.findByEmail()`, `RoomRepository.findActiveByOwnerId()`, `VoteRepository.findByRoundId()`). Use reactive return types (`Uni<>`, `Multi<>`).
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Entity classes from I1.T4
        *   Common query patterns from architecture blueprint (e.g., user lookup by email, rooms by owner)
        *   Panache repository patterns from Quarkus docs
    *   **Input Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/user/User.java` (and other entity files)
        *   `.codemachine/artifacts/architecture/03_System_Structure_and_Data.md` (indexing strategy shows common queries)
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/OrgMemberRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoomParticipantRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/RoundRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/PaymentHistoryRepository.java`
        *   `backend/src/main/java/com/scrumpoker/repository/AuditLogRepository.java`
    *   **Deliverables:**
        *   12 Panache repository classes implementing `PanacheRepositoryBase<Entity, UUID>`
        *   Custom finder methods with reactive return types (`Uni<User>`, `Multi<Room>`)
        *   Query methods using Panache query syntax (e.g., `find("email", email).firstResult()`)
        *   ApplicationScoped CDI beans for dependency injection
    *   **Acceptance Criteria:**
        *   Maven compilation successful
        *   Repositories injectable via `@Inject` in service classes
        *   Custom finder methods return correct reactive types
        *   Query methods execute without errors against seeded database
        *   Integration test for each repository demonstrates CRUD operations work
    *   **Dependencies:** [I1.T4]
    *   **Parallelizable:** No (depends on entity classes)
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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/java/com/scrumpoker/repository/UserRepository.java`
    *   **Summary:** Defines the reactive Panache repository for `User`, exposing helpers such as `findByEmail`, `findByOAuthProviderAndSubject`, `findActiveByEmail`, and `countActive`, all returning `Uni` results and filtering out soft-deleted rows where needed.
    *   **Recommendation:** Follow the same conventions for other user-centric queries: use property names (e.g., `oauthProvider`, `deletedAt`) and prefer `firstResult()`/`list()` for reactive operations so higher layers can compose `Uni`/`Multi` chains without blocking.
*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoomRepository.java`
    *   **Summary:** Implements `PanacheRepositoryBase<Room, String>` (Room IDs are 6-character strings) and already includes methods for owner/org lookups, privacy filters, inactivity checks, and count helpers.
    *   **Recommendation:** Mirror this approach when adding any new room queries: always filter on `deletedAt is null`, keep ordering deterministic (e.g., `lastActiveAt desc`), and return `Uni<List<Room>>` or `Uni<Long>` to remain reactive-friendly.
*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** Provides the full suite of vote lookups (`findByRoundId`, `findByRoomIdAndRoundNumber`, `findByParticipantId`, etc.) plus aggregation helpers like `countByRoundId`. Queries traverse relationships such as `round.room.roomId` when needed.
    *   **Recommendation:** When you're authoring similar finder methods for other entities, model the JPQL paths exactly as mapped in the entities (`round.roundId`, `participant.participantId`) and keep the return types as `Uni<List<...>>` for lists or `Uni<Vote>` for singletons.
*   **File:** `backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java`
    *   **Summary:** Because SessionHistory uses an `@EmbeddedId`, every finder uses native SQL through `Panache.getSession()` to work around Hibernate Reactive bugs, providing helpers for room/date lookups, counts, and combined owner/date filters.
    *   **Recommendation:** Leave the native-query pattern in place for composite-key tables; if you need new queries, build them as SQL strings executed via `Panache.getSession()` and documented with the bug link so future maintainers know why JPQL isn’t used.

### Implementation Tips & Notes
*   **Tip:** All repositories must be annotated with `@ApplicationScoped` and implement `PanacheRepositoryBase<Entity, KeyType>` so they can be injected into services without manual bean definitions.
*   **Tip:** Prefer Mutiny's `Uni` for single-result queries (`firstResult()`, `singleResultOptional()`) and `Uni<List<...>>` for multi-row fetches—`Multi` is only necessary if you plan to stream results; existing code almost exclusively returns `Uni`.
*   **Tip:** For soft-deletable entities (`User`, `Room`), always include `deletedAt is null` in finder predicates unless intentionally retrieving archived rows.
*   **Note:** Room IDs are strings while most other entities use UUIDs; declare repository generics accordingly to avoid ClassCastExceptions.
*   **Note:** SessionHistory’s composite key plus monthly partitions require native SQL finders; copying the established template prevents the Hibernate Reactive `EmbeddableInitializerImpl` bug cited in the comments.
*   **Warning:** Repositories already exist for the listed entities—review their current method sets before adding new APIs to avoid duplication, and keep method names descriptive so the unit and integration tests (I1.T8) can exercise each path explicitly.
