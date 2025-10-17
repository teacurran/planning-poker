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
  "inputs": "Entity classes from I1.T4, Common query patterns from architecture blueprint (e.g., user lookup by email, rooms by owner), Panache repository patterns from Quarkus docs",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/user/User.java",
    ".codemachine/artifacts/architecture/03_System_Structure_and_Data.md"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/repository/UserRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/UserPreferenceRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/OrganizationRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/OrgMemberRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/RoomRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/RoomParticipantRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/RoundRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/VoteRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/SubscriptionRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/PaymentHistoryRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/AuditLogRepository.java"
  ],
  "deliverables": "12 Panache repository classes implementing `PanacheRepositoryBase<Entity, UUID>`, Custom finder methods with reactive return types (`Uni<User>`, `Multi<Room>`), Query methods using Panache query syntax (e.g., `find(\"email\", email).firstResult()`), ApplicationScoped CDI beans for dependency injection",
  "acceptance_criteria": "Maven compilation successful, Repositories injectable via `@Inject` in service classes, Custom finder methods return correct reactive types, Query methods execute without errors against seeded database, Integration test for each repository demonstrates CRUD operations work",
  "dependencies": [
    "I1.T4"
  ],
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

### Context: key-components (from 01_Plan_Overview_and_Setup.md)

```markdown
*   **Key Components/Services:**
    *   **REST Controllers:** HTTP endpoints for user management, room CRUD, subscriptions, reporting
    *   **WebSocket Handlers:** Real-time connection managers for `/ws/room/{roomId}` endpoints
    *   **Domain Services:**
        *   User Service (registration, profile, preferences)
        *   Room Service (creation, configuration, join logic)
        *   Voting Service (vote casting, reveal, consensus calculation)
        *   Billing Service (subscription tier enforcement, Stripe integration)
        *   Reporting Service (session aggregation, analytics, export)
        *   Organization Service (SSO config, member management, admin controls)
    *   **Repository Layer:** Panache repositories for User, Room, Vote, Session, Subscription, Organization entities
    *   **Integration Adapters:** OAuth2 client, SSO adapter, Stripe adapter, Email adapter
    *   **Event Publisher/Subscriber:** Redis Pub/Sub client for WebSocket message broadcasting
    *   **Background Worker:** Async job processor for report generation, email dispatch
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/pom.xml`
    *   **Summary:** This file defines the Maven project configuration with Quarkus 3.15.1 and includes the `quarkus-hibernate-reactive-panache` dependency which is required for reactive Panache repositories.
    *   **Recommendation:** You MUST NOT modify this file - all required dependencies are already configured (Hibernate Reactive Panache, PostgreSQL reactive driver).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/user/User.java`
    *   **Summary:** This is a JPA entity class extending `PanacheEntityBase` with UUID primary key (`userId`), email, OAuth fields, subscription tier, and soft delete support via `deletedAt`.
    *   **Recommendation:** You MUST import this entity class in `UserRepository`. Note that the primary key field is named `userId` (not `id`), which is important for repository generic type parameters.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Room.java`
    *   **Summary:** This entity has a **non-UUID primary key** - it uses a 6-character String `roomId` field instead of UUID. This is critical for repository implementation.
    *   **Recommendation:** You MUST use `PanacheRepositoryBase<Room, String>` (not UUID) for RoomRepository since the primary key is a String.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
    *   **Summary:** This entity demonstrates proper relationships with `@ManyToOne` associations to `Round` and `RoomParticipant` entities, with UUID primary key `voteId`.
    *   **Recommendation:** When implementing `VoteRepository`, you can add custom finder methods that leverage these relationships (e.g., `findByRoundId`).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java`
    *   **Summary:** This entity shows a polymorphic pattern using `entity_id` and `entity_type` fields to reference either User or Organization entities.
    *   **Recommendation:** For `SubscriptionRepository`, you SHOULD add custom finders like `findActiveByEntityIdAndType(UUID entityId, EntityType entityType, SubscriptionStatus status)` to handle this polymorphic pattern.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/package-info.java`
    *   **Summary:** This package documentation indicates that all repositories should extend `PanacheRepositoryBase` and use reactive Mutiny types (`Uni`, `Multi`).
    *   **Recommendation:** You MUST follow this convention - use `PanacheRepositoryBase<Entity, IdType>` as the base interface and return reactive types.

*   **Directory:** `backend/src/main/java/com/scrumpoker/domain/`
    *   **Summary:** All entity classes are organized in domain subpackages: `user/`, `room/`, `billing/`, `organization/`. I confirmed 12 entity classes exist across these packages.
    *   **Recommendation:** You MUST create one repository for each entity class, matching the entity names exactly.

### Implementation Tips & Notes

*   **Tip:** Quarkus Panache repositories using `PanacheRepositoryBase` pattern MUST be annotated with `@ApplicationScoped` to be CDI beans injectable via `@Inject`.

*   **Tip:** All custom finder methods MUST return reactive types:
    - Single entity: `Uni<Entity>` (may be empty, so consider `Uni<Optional<Entity>>` or nullable)
    - Multiple entities: `Multi<Entity>`
    - For counting operations: `Uni<Long>`

*   **Tip:** Panache provides built-in query methods. The most common pattern for custom finders is:
    ```java
    public Uni<User> findByEmail(String email) {
        return find("email", email).firstResult();
    }
    ```

*   **Tip:** For queries excluding soft-deleted records, use:
    ```java
    public Multi<Room> findActiveByOwnerId(UUID ownerId) {
        return find("owner.userId = ?1 and deletedAt is null", ownerId).stream();
    }
    ```

*   **Note:** The Room entity uses a **String primary key** (6-character nanoid), not UUID. This affects the repository generic type parameter - you MUST use `PanacheRepositoryBase<Room, String>`.

*   **Note:** SessionHistory and AuditLog entities use composite keys (`SessionHistoryId`, `AuditLogId` classes exist). You MUST use these composite key classes as the ID type parameter for their repositories.

*   **Warning:** Some entities have composite primary keys defined in separate classes (e.g., `OrgMemberId`, `AuditLogId`, `SessionHistoryId`). You MUST check each entity's `@Id` annotations and use the correct ID type in the repository generic parameter.

*   **Warning:** Based on the indexing strategy in the architecture, you SHOULD implement these high-priority custom finder methods:
    - `UserRepository`: `findByEmail(String)`, `findByOAuthProviderAndSubject(String, String)`
    - `RoomRepository`: `findActiveByOwnerId(UUID)`, `findByOrgId(UUID)`
    - `VoteRepository`: `findByRoundId(UUID)`
    - `RoundRepository`: `findByRoomIdAndRoundNumber(String, Integer)`
    - `SubscriptionRepository`: `findActiveByEntityIdAndType(UUID, EntityType)`

*   **Best Practice:** Always use named parameters or positional parameters (`:paramName` or `?1`) in JPQL queries to prevent SQL injection, even though Panache helps with this.

*   **Best Practice:** Import statements should include:
    - `io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase`
    - `jakarta.enterprise.context.ApplicationScoped`
    - `io.smallrye.mutiny.Uni`
    - `io.smallrye.mutiny.Multi`
    - The specific entity class from `com.scrumpoker.domain.*`

*   **Critical:** I have verified the entity classes. Here are the correct ID types:
    - **User**: UUID (`userId`)
    - **UserPreference**: UUID (`userId` - same as User, it's a 1:1 relationship)
    - **Organization**: UUID (`orgId`)
    - **OrgMember**: Composite key using `OrgMemberId` class
    - **Room**: String (`roomId` - 6 characters)
    - **RoomParticipant**: UUID (`participantId`)
    - **Round**: UUID (`roundId`)
    - **Vote**: UUID (`voteId`)
    - **SessionHistory**: Composite key using `SessionHistoryId` class
    - **Subscription**: UUID (`subscriptionId`)
    - **PaymentHistory**: UUID (`paymentId`)
    - **AuditLog**: Composite key using `AuditLogId` class

*   **Critical:** For composite key entities (OrgMember, SessionHistory, AuditLog), you MUST read the composite key class files to understand their structure before implementing the repositories. These likely contain the combination of fields that make up the primary key.
