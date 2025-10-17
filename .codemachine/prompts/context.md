# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I1.T3",
  "iteration_id": "I1",
  "iteration_goal": "Establish project scaffolding, configure development environment, define database schema, and set up CI/CD foundation to enable parallel backend and frontend development in subsequent iterations.",
  "description": "Create Flyway migration scripts for all 11 core entities: User, UserPreference, Organization, OrgMember, Room, RoomParticipant, Round, Vote, SessionHistory, Subscription, PaymentHistory, AuditLog. Define tables with proper column types (UUID primary keys, VARCHAR lengths, TIMESTAMP with timezone, JSONB for configurations), foreign key constraints, indexes (see indexing strategy in ERD section), and partitioning setup for SessionHistory and AuditLog (monthly range partitions). Include `deleted_at` timestamp for soft deletes on User and Room tables.",
  "agent_type_hint": "DatabaseAgent",
  "inputs": "Entity Relationship Diagram from architecture blueprint (Section 3.6), Data model overview with entity descriptions, Indexing strategy specifications, Partitioning requirements (monthly partitions for SessionHistory, AuditLog)",
  "input_files": [
    ".codemachine/artifacts/architecture/03_System_Structure_and_Data.md"
  ],
  "target_files": [
    "backend/src/main/resources/db/migration/V1__initial_schema.sql",
    "backend/src/main/resources/db/migration/V2__create_partitions.sql",
    "backend/src/main/resources/db/migration/V3__create_indexes.sql"
  ],
  "deliverables": "SQL DDL scripts creating all 11 tables with correct column definitions, Foreign key constraints enforcing referential integrity, Indexes on high-priority columns (User.email, Room.owner_id, Vote.round_id, etc.), Partition creation setup for SessionHistory and AuditLog tables, Soft delete columns (`deleted_at`) on User and Room",
  "acceptance_criteria": "Migration scripts execute without errors on PostgreSQL 15, All foreign key relationships validated (cascading deletes/nulls as specified), Query plan analysis confirms indexes used for common queries (e.g., `EXPLAIN SELECT * FROM room WHERE owner_id = ?`), Partitions created for current and next 3 months for SessionHistory, Schema matches ERD entity specifications exactly",
  "dependencies": [
    "I1.T1"
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

### Context: Entity Relationship Diagram (from 03_System_Structure_and_Data.md)

The ERD shows these key relationships:
- User 1:1 UserPreference (cascade delete)
- User 1:N OrgMember (user can belong to multiple orgs)
- User 1:N Room (user owns multiple rooms)
- User 1:1 Subscription (user has one subscription)
- Organization 1:N OrgMember (org has multiple members)
- Organization 1:N Room (org restricts rooms)
- Organization 1:1 Subscription (org has one subscription)
- Organization 1:N AuditLog (org generates audit events)
- Room 1:N RoomParticipant (room has multiple participants)
- Room 1:N Round (room contains multiple rounds)
- Room 1:N SessionHistory (room records session history)
- Round 1:N Vote (round collects votes)
- RoomParticipant 1:N Vote (participant casts votes)
- Subscription 1:N PaymentHistory (subscription has payment history)
- User 1:N AuditLog (user performs audited actions)

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/pom.xml`
    *   **Summary:** This file contains the complete Quarkus Maven project configuration with all required reactive extensions, database drivers, and Flyway migration support already configured.
    *   **Recommendation:** The POM already includes `quarkus-flyway` (line 95) and `quarkus-jdbc-postgresql` (line 99), so you do NOT need to add any dependencies. The project is ready for Flyway migrations.

*   **File:** `backend/src/main/resources/application.properties`
    *   **Summary:** This file contains all database and Flyway configuration. Note that Flyway migrations are currently DISABLED by default (`quarkus.flyway.migrate-at-start=${FLYWAY_MIGRATE:false}` on line 30) to allow dev mode startup without a database.
    *   **Recommendation:** The migration location is already configured as `classpath:db/migration` (line 31). You MUST create the migration files in the directory `backend/src/main/resources/db/migration/`. After completing this task, migrations can be enabled by setting the environment variable `FLYWAY_MIGRATE=true` or updating the property to `quarkus.flyway.migrate-at-start=true`.

*   **File:** `pom.xml` (root)
    *   **Summary:** This is the root project POM with minimal configuration.
    *   **Recommendation:** You should focus on the backend module's POM (`backend/pom.xml`) for all backend configuration, not this root POM.

### Implementation Tips & Notes

*   **CRITICAL: PostgreSQL Version:** The project uses **PostgreSQL 15** as specified in the architecture blueprint. You MUST use PostgreSQL-specific features like:
    - `UUID` data type with `gen_random_uuid()` function for default values
    - `JSONB` data type for flexible configuration storage
    - `TIMESTAMP WITH TIME ZONE` for all timestamp columns
    - PostgreSQL ENUM types using `CREATE TYPE` statements
    - Range partitioning with `PARTITION BY RANGE` for SessionHistory and AuditLog

*   **CRITICAL: Entity Count:** The ERD diagram shows **11 entities** that you must create tables for: User, UserPreference, Organization, OrgMember, Room, RoomParticipant, Round, Vote, SessionHistory, Subscription, PaymentHistory, AuditLog. All 11 tables MUST be present in the schema.

*   **CRITICAL: Room ID Type:** The architecture blueprint specifies that **Room.room_id uses a 6-character nanoid** (VARCHAR(6)), NOT UUID. This is critical for generating short, shareable room links like `https://app.com/room/abc123`.

*   **CRITICAL: Soft Deletes:** **Soft deletes** are required on User and Room tables only. You MUST include a `deleted_at TIMESTAMP WITH TIME ZONE` column on these two tables. Use partial indexes like `WHERE deleted_at IS NULL` for queries on active records.

*   **CRITICAL: JSONB Columns:** The following columns MUST be defined as JSONB type:
    - `UserPreference.default_room_config`
    - `UserPreference.notification_settings`
    - `Organization.sso_config`
    - `Organization.branding`
    - `Room.config`
    - `SessionHistory.participants`
    - `SessionHistory.summary_stats`

*   **CRITICAL: Monthly Partitions:** The acceptance criteria states you must create partitions for "current and next 3 months" (4 partitions total). Calculate the current month dynamically or use a fixed date range (e.g., 2025-01-01 to 2025-05-01 covering Jan, Feb, Mar, Apr). Use PostgreSQL's declarative partitioning:
    ```sql
    CREATE TABLE session_history_2025_01 PARTITION OF session_history
        FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
    ```

*   **CRITICAL: ENUM Types:** You MUST create PostgreSQL ENUM types for the following columns:
    - `subscription_tier_enum`: 'FREE', 'PRO', 'PRO_PLUS', 'ENTERPRISE'
    - `privacy_mode_enum`: 'PUBLIC', 'INVITE_ONLY', 'ORG_RESTRICTED'
    - `room_role_enum`: 'HOST', 'VOTER', 'OBSERVER'
    - `org_role_enum`: 'ADMIN', 'MEMBER'
    - `entity_type_enum`: 'USER', 'ORG'

*   **Note: Migration File Organization:** The task description specifies creating **3 separate migration files**:
    1. `V1__initial_schema.sql` - ENUM types and all table definitions
    2. `V2__create_partitions.sql` - Partition setup for SessionHistory and AuditLog
    3. `V3__create_indexes.sql` - All indexes (high-priority and composite indexes)

    This separation allows for cleaner organization and easier debugging.

*   **Note: Foreign Key Cascades:** Based on the ERD relationships, use these cascade rules:
    - `ON DELETE CASCADE` for dependent entities where the child has no meaning without the parent:
      - UserPreference → User
      - PaymentHistory → Subscription
      - Vote → Round
      - Round → Room
    - `ON DELETE SET NULL` for nullable optional relationships:
      - Room.owner_id → User (allows anonymous rooms to persist if owner deletes account)
      - Room.org_id → Organization (rooms can exist without org restriction)
      - RoomParticipant.user_id → User (anonymous participants have NULL user_id)
    - `ON DELETE RESTRICT` or no cascade for references that should prevent deletion:
      - Organization.subscription_id → Subscription (can't delete subscription while org exists)

*   **Note: Database Defaults:** Use database defaults where appropriate:
    - `user_id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
    - `created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP`
    - `subscription_tier subscription_tier_enum DEFAULT 'FREE'`

*   **Warning: INET Type:** The AuditLog.ip_address column should use PostgreSQL's `INET` data type (not VARCHAR), which provides proper IP address storage and validation for both IPv4 and IPv6 addresses.

*   **Warning: Numeric Precision:** The Vote.average column should use `NUMERIC(5,2)` to store decimal values like "8.33" (max 999.99). The median column is VARCHAR(10) because Fibonacci cards can include non-numeric values like "?", "∞", "☕".

*   **Tip: Index Naming Convention:** Use descriptive index names following PostgreSQL conventions:
    - Primary keys: `pk_user`, `pk_room`
    - Foreign keys: `fk_user_preference_user_id`, `fk_room_owner_id`
    - Indexes: `idx_user_email`, `idx_room_owner_id_created_at`
    - Unique constraints: `uq_user_email`, `uq_subscription_stripe_id`

*   **Tip: Comments:** Consider adding SQL comments to document complex columns:
    ```sql
    COMMENT ON COLUMN room.config IS 'JSONB configuration: deck_type, timer_enabled, timer_duration_seconds, reveal_behavior';
    ```

*   **Tip: Verification Commands:** After migrations, verify with these SQL queries:
    - Check tables: `\dt` or `SELECT tablename FROM pg_tables WHERE schemaname = 'public';`
    - Check partitions: `SELECT tablename FROM pg_tables WHERE tablename LIKE 'session_history_%';`
    - Check indexes: `\di` or `SELECT indexname, tablename FROM pg_indexes WHERE schemaname = 'public';`
    - Test query plan: `EXPLAIN SELECT * FROM room WHERE owner_id = '123e4567-e89b-12d3-a456-426614174000';`

*   **Critical: Schema Accuracy:** The acceptance criteria states that **"Schema matches ERD entity specifications exactly"**. You MUST ensure every column, data type, constraint, and relationship specified in the ERD and entity table above is implemented precisely as documented. Any deviation will cause the task to fail acceptance.

### Directory Structure Confirmation

I have confirmed the following directory structure exists:
- `backend/src/main/resources/` - EXISTS
- `backend/src/main/resources/application.properties` - EXISTS

You MUST create the following directory and files:
- `backend/src/main/resources/db/migration/` - DIRECTORY TO CREATE
- `backend/src/main/resources/db/migration/V1__initial_schema.sql` - FILE TO CREATE
- `backend/src/main/resources/db/migration/V2__create_partitions.sql` - FILE TO CREATE
- `backend/src/main/resources/db/migration/V3__create_indexes.sql` - FILE TO CREATE

### Testing Strategy

After creating the migration scripts:

1. **Enable Flyway migrations:** Set `quarkus.flyway.migrate-at-start=true` in `application.properties` or set environment variable `FLYWAY_MIGRATE=true`

2. **Start PostgreSQL:** Use Docker:
   ```bash
   docker run --name postgres-scrumpoker -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=scrumpoker -p 5432:5432 -d postgres:15
   ```

3. **Run migrations:** Start Quarkus in dev mode:
   ```bash
   cd backend
   mvn clean compile quarkus:dev
   ```

4. **Verify schema:** Connect to PostgreSQL and run:
   ```sql
   \dt  -- List all tables (should show 11 tables)
   SELECT tablename FROM pg_tables WHERE tablename LIKE 'session_history_%';  -- Check partitions
   \di  -- List all indexes
   ```

5. **Test queries:** Verify indexes are used:
   ```sql
   EXPLAIN SELECT * FROM room WHERE owner_id = gen_random_uuid();
   EXPLAIN SELECT * FROM "user" WHERE email = 'test@example.com';
   ```
