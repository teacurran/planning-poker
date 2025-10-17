# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T4",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Implement JPA entity classes for all domain entities using Hibernate Reactive Panache. Define entities: `User`, `UserPreference`, `Organization`, `OrgMember`, `Room`, `RoomParticipant`, `Round`, `Vote`, `SessionHistory`, `Subscription`, `PaymentHistory`, `AuditLog`. Use `@Entity` annotations, proper column mappings (including JSONB with `@Type(JsonBinaryType.class)` from hibernate-types), relationships (`@OneToMany`, `@ManyToOne`), and validation constraints (`@NotNull`, `@Size`). Extend `PanacheEntityBase` for custom ID types (UUID). Add `@Cacheable` annotations where appropriate.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Database migration scripts from I1.T3, Entity attribute specifications from ERD, JPA/Panache patterns from Quarkus documentation",
  "input_files": [
    "backend/src/main/resources/db/migration/V1__initial_schema.sql",
    ".codemachine/artifacts/architecture/03_System_Structure_and_Data.md"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/user/User.java",
    "backend/src/main/java/com/scrumpoker/domain/user/UserPreference.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/Organization.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/OrgMember.java",
    "backend/src/main/java/com/scrumpoker/domain/room/Room.java",
    "backend/src/main/java/com/scrumpoker/domain/room/RoomParticipant.java",
    "backend/src/main/java/com/scrumpoker/domain/room/Round.java",
    "backend/src/main/java/com/scrumpoker/domain/room/Vote.java",
    "backend/src/main/java/com/scrumpoker/domain/room/SessionHistory.java",
    "backend/src/main/java/com/scrumpoker/domain/billing/Subscription.java",
    "backend/src/main/java/com/scrumpoker/domain/billing/PaymentHistory.java",
    "backend/src/main/java/com/scrumpoker/domain/organization/AuditLog.java"
  ],
  "deliverables": "12 JPA entity classes with correct annotations, Bidirectional relationships configured (e.g., User ↔ Room ownership), JSONB column mappings for Room.config, UserPreference.default_room_config, Organization.sso_config, Bean validation constraints matching database constraints, UUID generators configured for primary keys",
  "acceptance_criteria": "Maven compilation successful, Quarkus dev mode starts without JPA mapping errors, Entities can be persisted and retrieved via Panache repository methods, JSONB columns serialize/deserialize correctly (test with sample data), Foreign key relationships navigable in code (e.g., `room.getOwner()`)",
  "dependencies": [
    "I1.T3"
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

#### Entity Relationship Diagram (PlantUML)

[Entity relationships documented showing]:
- User ||--o| UserPreference : has
- User ||--o{ OrgMember : belongs_to
- User ||--o{ Room : owns
- User ||--o| Subscription : subscribes
- Organization ||--o{ OrgMember : contains
- Organization ||--o{ Room : restricts
- Organization ||--|| Subscription : pays_via
- Organization ||--o{ AuditLog : generates
- Room ||--o{ RoomParticipant : hosts
- Room ||--o{ Round : contains
- Room ||--o{ SessionHistory : records
- Round ||--o{ Vote : collects
- RoomParticipant ||--o{ Vote : casts
- Subscription ||--o{ PaymentHistory : has
- User ||--o{ AuditLog : performs

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

### Context: technology-stack-summary (from 02_Architecture_Overview.md)

```markdown
### 3.2. Technology Stack Summary

| **Category** | **Technology Choice** | **Justification** |
|--------------|----------------------|-------------------|
| **Backend Framework** | **Quarkus 3.x (Reactive)** | Specified requirement, optimized for cloud-native deployment, reactive runtime for WebSocket concurrency, fast startup times |
| **Language** | **Java 17+ (LTS)** | Native Quarkus support, strong type system, mature ecosystem, team expertise |
| **ORM/Data Access** | **Hibernate Reactive + Panache** | Specified requirement, reactive database access with Mutiny streams, simplified repository pattern via Panache |
| **Database** | **PostgreSQL 15+** | ACID compliance, JSONB for flexible room configuration storage, proven scalability, strong community support |

#### Key Libraries & Extensions

**Backend (Quarkus):**
- `quarkus-resteasy-reactive-jackson` - Reactive REST endpoints with JSON serialization
- `quarkus-hibernate-reactive-panache` - Reactive database access layer
- `quarkus-reactive-pg-client` - Non-blocking PostgreSQL driver
- `quarkus-redis-client` - Redis integration for caching and Pub/Sub
- `quarkus-websockets` - WebSocket server implementation
- `quarkus-oidc` - OAuth2/OIDC authentication and SSO support
- `quarkus-smallrye-jwt` - JWT token generation and validation
- `quarkus-micrometer-registry-prometheus` - Metrics export
- `stripe-java` - Stripe API client for payment processing
```

### Context: component-diagram (from 03_System_Structure_and_Data.md)

```markdown
### 3.5. Component Diagram(s) (C4 Level 3 or UML)

#### Description

This Component Diagram zooms into the **Quarkus Application** container to reveal its internal modular structure. The application follows a hexagonal (ports and adapters) architecture with clear separation between domain logic, infrastructure, and API layers.

**Key Modules:**
- **REST Controllers:** HTTP endpoint handlers exposing RESTful APIs for user management, room CRUD, subscriptions, and reporting
- **WebSocket Handlers:** Real-time connection managers processing vote events, room state changes, and participant actions
- **Domain Services:** Core business logic implementing estimation rules, room lifecycle, user preferences, billing logic
- **Repository Layer:** Data access abstractions using Hibernate Reactive Panache for PostgreSQL interactions
- **Integration Adapters:** External service clients (OAuth2, Stripe, email) following the adapter pattern
- **Event Publisher:** Redis Pub/Sub integration for broadcasting WebSocket messages across application nodes
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/pom.xml`
    *   **Summary:** This file contains the Maven project configuration with Quarkus 3.15.1, all required dependencies for Hibernate Reactive Panache, PostgreSQL reactive driver, and Flyway migrations are already configured.
    *   **Recommendation:** You MUST use the existing dependencies. The project already has `quarkus-hibernate-reactive-panache` and `quarkus-reactive-pg-client` configured. DO NOT add additional dependencies for basic JPA entity functionality.

*   **File:** `backend/src/main/resources/db/migration/V1__initial_schema.sql`
    *   **Summary:** This file contains the complete database schema with all 11 tables, ENUM types, foreign key constraints, and check constraints. It is the **single source of truth** for the entity structure.
    *   **Recommendation:** You MUST reference this SQL file directly to ensure your JPA entity annotations match the database schema EXACTLY. Pay special attention to:
        - Table names (note: "user" table is quoted in SQL, use `@Table(name = "\"user\"")`)
        - Column names (e.g., `subscription_tier`, `oauth_provider`, `deleted_at`)
        - Enum type mappings (e.g., `subscription_tier_enum`, `privacy_mode_enum`)
        - Nullable columns (e.g., `owner_id`, `org_id` in Room)
        - JSONB columns (e.g., `config` in Room, `sso_config` in Organization)
        - Unique constraints (e.g., `email` in User, composite unique on `oauth_provider` + `oauth_subject`)
        - Composite primary keys (e.g., `(org_id, user_id)` in OrgMember)
        - Partitioned tables (SessionHistory and AuditLog use composite PK with partition key)

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This file contains database configuration, Hibernate settings, and Flyway configuration. Hibernate is configured with `quarkus.hibernate-orm.database.generation=validate` which means it will validate entities against the database schema.
    *   **Recommendation:** Your entities MUST match the database schema exactly or Quarkus will fail to start. The `validate` mode will catch any mismatches during development.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/package-info.java`
    *   **Summary:** This file documents the domain package structure with subpackages: `user`, `room`, `billing`, `reporting`, `organization`.
    *   **Recommendation:** You MUST place each entity in its correct subpackage as documented:
        - `User`, `UserPreference` → `com.scrumpoker.domain.user`
        - `Room`, `RoomParticipant`, `Round`, `Vote`, `SessionHistory` → `com.scrumpoker.domain.room`
        - `Subscription`, `PaymentHistory` → `com.scrumpoker.domain.billing`
        - `Organization`, `OrgMember`, `AuditLog` → `com.scrumpoker.domain.organization`

### Implementation Tips & Notes

*   **Tip: Hibernate Reactive Panache Base Class:** For entities with UUID primary keys, you MUST extend `io.quarkus.hibernate.reactive.panache.PanacheEntityBase` (NOT `PanacheEntity`) because you are using custom UUID IDs, not auto-generated Long IDs. The standard `PanacheEntity` uses `@GeneratedValue` with Long IDs.

*   **Tip: JSONB Type Mapping:** For JSONB columns, you need to handle them carefully in Hibernate Reactive. The standard approach is:
    - Import: `import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;`
    - Use `@Column(columnDefinition = "jsonb")` on JSONB fields
    - Map JSONB to `String` or use custom converters/serializers for complex objects
    - For simple storage, use `String` and serialize/deserialize manually with Jackson
    - DO NOT use `@Type(JsonBinaryType.class)` from hibernate-types as mentioned in the task description - this is for traditional Hibernate, not Hibernate Reactive. Quarkus Reactive uses a different approach.

*   **Tip: PostgreSQL ENUM Types:** The database uses PostgreSQL ENUM types (e.g., `subscription_tier_enum`). In JPA entities:
    - Create Java enums matching the database enum values exactly (case-sensitive)
    - Use `@Enumerated(EnumType.STRING)` annotation
    - Hibernate will automatically map between Java enum and PostgreSQL enum

*   **Tip: Soft Delete Pattern:** The `User` and `Room` entities have `deleted_at` columns for soft deletes. You SHOULD:
    - Add a `@Column(name = "deleted_at")` field of type `java.time.Instant`
    - Make it nullable (default null means not deleted)
    - You do NOT need to add `@Where` clauses yet - that will be handled in future iterations by repository methods

*   **Tip: Bidirectional Relationships:** When creating bidirectional relationships (e.g., User ↔ Room, Room ↔ Round):
    - Use `mappedBy` on the non-owning side (the side WITHOUT the foreign key)
    - Use `@ManyToOne` on the owning side (the side WITH the foreign key)
    - Use `@OneToMany(mappedBy = "fieldName")` on the inverse side
    - Example: Room has `owner_id` FK → Room entity has `@ManyToOne User owner`, User entity has `@OneToMany(mappedBy = "owner") List<Room> rooms`

*   **Tip: Composite Primary Keys:** For entities with composite PKs (OrgMember, partitioned tables):
    - OrgMember: Use `@EmbeddedId` with a separate `@Embeddable` class for the composite key (OrgMemberId containing orgId and userId)
    - SessionHistory and AuditLog: Use `@IdClass` approach since they have composite PK with partition key

*   **Tip: Bean Validation:** You SHOULD add Bean Validation annotations (`@NotNull`, `@Size`, `@Email`) to match database constraints. This provides validation at the application layer before hitting the database. Examples:
    - `@NotNull` for non-nullable columns
    - `@Size(max = 255)` for VARCHAR(255) columns
    - `@Email` for email fields

*   **Warning: Quarkus Dev Mode:** Quarkus dev mode uses hot reload. If your entities have syntax errors or mapping errors, Quarkus will fail to start and show detailed error messages in the console. Read these carefully - they usually point directly to the problem (e.g., "column 'xyz' not found in table 'abc'").

*   **Warning: Flyway Validation:** The Flyway migration scripts have already created the database schema. Your entities must align with the existing schema. If you make a mistake, Hibernate's `validate` mode will fail on startup with a clear error message about schema mismatch.

*   **Note: Caching Strategy:** The task mentions adding `@Cacheable` annotations. For now, you SHOULD add `@Cacheable` to entities that will be frequently read and rarely updated:
    - User (frequently accessed for authentication/authorization)
    - Organization (read-heavy, rarely changes)
    - Room (moderate caching, frequently accessed during active sessions)
    - DO NOT cache: Vote, SessionHistory, AuditLog (write-heavy or append-only)

*   **Note: Timestamp Fields:** Use `java.time.Instant` for all timestamp fields (e.g., `created_at`, `updated_at`, `deleted_at`). This maps directly to PostgreSQL's `TIMESTAMP WITH TIME ZONE`. You SHOULD also add `@CreationTimestamp` and `@UpdateTimestamp` annotations from Hibernate for automatic timestamp management.

*   **Note: UUID Generation:** For UUID primary keys, use `@GeneratedValue(strategy = GenerationType.AUTO)` or explicitly use `@GeneratedValue(generator = "UUID")` with `@GenericGenerator`. Quarkus will handle UUID generation automatically.

*   **Critical: Room ID is NOT UUID:** The `Room` entity uses a 6-character VARCHAR primary key (nanoid), NOT a UUID. DO NOT use UUID type for Room.room_id. Use `String` type and do NOT add `@GeneratedValue` - room IDs will be generated by application logic in future iterations.

*   **Critical: Partitioned Tables:** SessionHistory and AuditLog use composite primary keys because they are partitioned tables. The primary key MUST include the partition key (`started_at` for SessionHistory, `timestamp` for AuditLog). Use the format:
    ```java
    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Id
    @Column(name = "started_at")
    private Instant startedAt;
    ```

### Recommended Approach

1. **Start with Simple Entities:** Begin with entities that have no relationships (e.g., Subscription, PaymentHistory) to verify your basic JPA setup works.

2. **Create Enum Classes First:** Define all Java enums (SubscriptionTier, PrivacyMode, RoomRole, OrgRole, etc.) before creating entities. Place these in appropriate domain packages.

3. **Build Entities Bottom-Up:** Create entities in dependency order:
   - First: Subscription (no FK dependencies except self-reference)
   - Second: User (references Subscription via subscription_tier, but this is an enum, not a FK in the current schema)
   - Third: UserPreference (references User)
   - Fourth: Organization (references Subscription)
   - Fifth: OrgMember (references Organization and User)
   - Continue with Room, RoomParticipant, Round, Vote, SessionHistory, PaymentHistory, AuditLog

4. **Test Incrementally:** After creating 2-3 entities, run `mvn compile` and then `mvn quarkus:dev` to verify:
   - Entities compile without errors
   - Quarkus starts successfully
   - Hibernate validation passes (no schema mismatch errors)
   - Fix any issues before proceeding to next entities

5. **Verify Relationships:** Once all entities are created, test relationship navigation:
   - Check that bidirectional relationships work both ways
   - Verify lazy loading configuration (use `FetchType.LAZY` for collections)
   - Ensure cascading is NOT configured yet (this will be added in future iterations when needed)

6. **Final Validation:** Run full Maven build (`mvn clean verify`) and start Quarkus dev mode to ensure:
   - All entities compile
   - Hibernate validates successfully against database schema
   - No runtime errors during entity initialization
