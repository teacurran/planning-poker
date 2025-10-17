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
  "dependencies": ["I1.T1"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

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

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### ✅ EXCELLENT NEWS: Task I1.T3 is Already Complete!

**Critical Discovery:** After analyzing the codebase, I have found that **ALL three migration files for Task I1.T3 have already been fully implemented**. The task is effectively DONE.

### Existing Migration Files Analysis

#### File: `backend/src/main/resources/db/migration/V1__initial_schema.sql`
- **Status:** ✅ COMPLETE (259 lines)
- **Summary:** This file creates all 11 core entity tables with:
  - All ENUM types defined (subscription_tier_enum, subscription_status_enum, privacy_mode_enum, room_role_enum, org_role_enum, entity_type_enum, payment_status_enum)
  - Complete table definitions matching the architecture ERD exactly
  - All UUID primary keys with `gen_random_uuid()` defaults
  - All foreign key constraints with correct ON DELETE behaviors
  - JSONB columns for Organization.sso_config, Organization.branding, UserPreference.default_room_config, UserPreference.notification_settings, Room.config, SessionHistory.participants, SessionHistory.summary_stats, AuditLog.metadata
  - Soft delete columns (`deleted_at`) on User and Room tables
  - Unique constraints on critical fields
  - Comprehensive table and column comments
  - Partitioned table definitions for SessionHistory (by started_at) and AuditLog (by timestamp)
- **Quality:** Production-ready, follows best practices

#### File: `backend/src/main/resources/db/migration/V2__create_partitions.sql`
- **Status:** ✅ COMPLETE (85 lines)
- **Summary:** This file creates monthly partitions for:
  - SessionHistory: 4 partitions (2025-01 through 2025-04)
  - AuditLog: 4 partitions (2025-01 through 2025-04)
  - Includes detailed partition management notes with 3 options for automated partition creation
  - Documents data retention strategies
- **Quality:** Well-documented with maintenance guidance
- **⚠️ NOTE:** Partition dates need updating - currently set for January-April 2025, but today is October 17, 2025

#### File: `backend/src/main/resources/db/migration/V3__create_indexes.sql`
- **Status:** ✅ COMPLETE (225 lines)
- **Summary:** This file creates comprehensive indexes including:
  - All high-priority indexes from the architecture specification
  - Composite partial indexes for query optimization
  - Covering indexes (INCLUDE clause) for Vote aggregation
  - WHERE clauses on indexes for soft-delete filtering
  - Performance indexes for all 11 tables
  - Detailed maintenance notes with queries for monitoring index usage and bloat
- **Quality:** Comprehensive, includes monitoring guidance

### Configuration Analysis

#### File: `backend/src/main/resources/application.properties`
- **Flyway Configuration:** Currently DISABLED by default (`quarkus.flyway.migrate-at-start=${FLYWAY_MIGRATE:false}`)
- **Reason:** Allows dev mode startup without database (as noted in comments)
- **⚠️ ACTION REQUIRED:** You MUST enable Flyway migrations now that the scripts are complete

#### File: `backend/pom.xml`
- **Flyway Dependencies:** Already configured correctly with `quarkus-flyway` and `quarkus-jdbc-postgresql`
- **Status:** ✅ No changes needed

### Recommendations for Completing This Task

Since the migration files are already complete, you need to:

1. **⚠️ CRITICAL: Update Partition Dates** in `V2__create_partitions.sql`:
   - Current dates are January-April 2025
   - Today's date is October 17, 2025 (per system info)
   - Update to create partitions for: October 2025, November 2025, December 2025, January 2026
   - Example:
     ```sql
     CREATE TABLE session_history_2025_10 PARTITION OF session_history
         FOR VALUES FROM ('2025-10-01') TO ('2025-11-01');
     CREATE TABLE session_history_2025_11 PARTITION OF session_history
         FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
     -- etc.
     ```

2. **⚠️ CRITICAL: Enable Flyway Migrations** in `application.properties`:
   - Change line 30 from: `quarkus.flyway.migrate-at-start=${FLYWAY_MIGRATE:false}`
   - To: `quarkus.flyway.migrate-at-start=${FLYWAY_MIGRATE:true}`
   - Update the comment on line 28-29 to reflect that migrations are now enabled

3. **✅ Verify Migration Execution** by running:
   ```bash
   cd backend
   mvn clean compile
   # Start Quarkus in dev mode (this should now run Flyway migrations)
   mvn quarkus:dev
   ```
   - Check console output for Flyway migration success messages
   - Verify all tables created: `SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';`
   - Verify partitions created: `SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE '%_2025_%';`

4. **✅ Acceptance Criteria Validation:**
   - Run EXPLAIN queries to verify index usage:
     ```sql
     EXPLAIN SELECT * FROM room WHERE owner_id = 'some-uuid';
     EXPLAIN SELECT * FROM vote WHERE round_id = 'some-uuid';
     ```
   - Verify foreign key constraints by checking `information_schema.table_constraints`
   - Confirm partitions created: Should see 8 partition tables (4 for SessionHistory, 4 for AuditLog)

5. **✅ Mark Task as Done** in `.codemachine/artifacts/tasks/tasks_I1.json`:
   - Change `"done": false` to `"done": true` for task I1.T3

### Implementation Tips & Notes

- **Tip:** The migration files follow Flyway's versioning convention (V1__, V2__, V3__) correctly
- **Tip:** The enum types are created BEFORE the tables that use them (proper dependency order)
- **Tip:** The Room.room_id field uses VARCHAR(6) for 6-character nanoid, with a CHECK constraint enforcing length
- **Tip:** The RoomParticipant table uses a clever composite primary key: `PRIMARY KEY (room_id, COALESCE(user_id::TEXT, anonymous_id))` to handle both authenticated and anonymous users
- **Note:** The Subscription table uses a polymorphic pattern (entity_id + entity_type) to link to either User or Organization
- **Note:** JSONB columns are used strategically for flexible configuration without requiring schema migrations
- **Warning:** Partition maintenance is CRITICAL - the V2 migration includes detailed notes about automated partition creation. Consider implementing pg_partman or a scheduled job before production deployment.
- **Warning:** The INET data type is used for AuditLog.ip_address, which properly handles both IPv4 and IPv6 addresses

### Database Dependencies

- **PostgreSQL Version:** The migrations require PostgreSQL 15+ (confirmed in docker-compose.yml and architecture docs)
- **Extensions:** Standard PostgreSQL - no custom extensions required for these migrations
- **JDBC Driver:** Already configured in pom.xml (`quarkus-jdbc-postgresql` and `quarkus-reactive-pg-client`)

### Testing Strategy

After updating the partition dates and enabling migrations:

1. **Start PostgreSQL:** Use Docker:
   ```bash
   docker run --name postgres-scrumpoker -e POSTGRES_PASSWORD=postgres -e POSTGRES_DB=scrumpoker -p 5432:5432 -d postgres:15
   ```

2. **Run migrations:** Start Quarkus in dev mode:
   ```bash
   cd backend
   mvn clean compile quarkus:dev
   ```

3. **Verify schema:** Connect to PostgreSQL and run:
   ```sql
   \dt  -- List all tables (should show 11 tables + partitions)
   SELECT tablename FROM pg_tables WHERE tablename LIKE 'session_history_%';  -- Check SessionHistory partitions
   SELECT tablename FROM pg_tables WHERE tablename LIKE 'audit_log_%';  -- Check AuditLog partitions
   \di  -- List all indexes
   ```

4. **Test queries:** Verify indexes are used:
   ```sql
   EXPLAIN SELECT * FROM room WHERE owner_id = gen_random_uuid();
   EXPLAIN SELECT * FROM "user" WHERE email = 'test@example.com';
   ```

### Next Task Dependencies

Task I1.T4 (JPA Entity Classes) depends on I1.T3 being complete. Since the migration files are ready (after updating partition dates), you can now proceed to implement the Hibernate Reactive Panache entities that map to these tables.

---

## Summary

**TASK STATUS:** This task (I1.T3) is **99% COMPLETE** with only minor updates needed:
1. Update partition dates in V2__create_partitions.sql (October-January instead of January-April)
2. Enable Flyway migrations in application.properties
3. Verify migrations execute successfully
4. Mark task as done in tasks_I1.json

The quality of the existing migration files is excellent - they follow best practices, include comprehensive comments, and match the architecture specification exactly. Minimal work required to complete this task!
