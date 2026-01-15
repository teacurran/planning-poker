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
  "inputs": "*   Entity Relationship Diagram from architecture blueprint (Section 3.6)\n        *   Data model overview with entity descriptions\n        *   Indexing strategy specifications\n        *   Partitioning requirements (monthly partitions for SessionHistory, AuditLog)",
  "target_files": [],
  "input_files": [],
  "deliverables": "*   SQL DDL scripts creating all 11 tables with correct column definitions\n        *   Foreign key constraints enforcing referential integrity\n        *   Indexes on high-priority columns (User.email, Room.owner_id, Vote.round_id, etc.)\n        *   Partition creation setup for SessionHistory and AuditLog tables\n        *   Soft delete columns (`deleted_at`) on User and Room",
  "acceptance_criteria": "*   Migration scripts execute without errors on PostgreSQL 15\n        *   All foreign key relationships validated (cascading deletes/nulls as specified)\n        *   Query plan analysis confirms indexes used for common queries (e.g., `EXPLAIN SELECT * FROM room WHERE owner_id = ?`)\n        *   Partitions created for current and next 3 months for SessionHistory\n        *   Schema matches ERD entity specifications exactly",
  "dependencies": [],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: data-model-overview-erd (from 03_System_Structure_and_Data.md)

```markdown
<!-- anchor: data-model-overview-erd -->
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
  story_title : VARCHAR(500)
  started_at : TIMESTAMP
  revealed_at : TIMESTAMP
  average : DECIMAL(5,2)
  median : VARCHAR(10)
  consensus_reached : BOOLEAN
}

entity Vote {
  *vote_id : UUID <<PK>>
  --
  round_id : UUID <<FK>>
  participant_id : UUID <<FK>>
  card_value : VARCHAR(10)
  voted_at : TIMESTAMP
}

entity SessionHistory {
  *session_id : UUID <<PK>>
  --
  room_id : VARCHAR(6) <<FK>>
  started_at : TIMESTAMP <<PARTITION KEY>>
  ended_at : TIMESTAMP
  total_rounds : INTEGER
  total_stories : INTEGER
  participants : JSONB
  summary_stats : JSONB
}

' Billing
entity Subscription {
  *subscription_id : UUID <<PK>>
  --
  stripe_subscription_id : VARCHAR(100) <<UNIQUE>>
  entity_id : UUID
  entity_type : ENUM(USER, ORG)
  tier : ENUM(FREE, PRO, PRO_PLUS, ENTERPRISE)
  status : VARCHAR(50)
  current_period_end : TIMESTAMP
  canceled_at : TIMESTAMP
  created_at : TIMESTAMP
}

entity PaymentHistory {
  *payment_id : UUID <<PK>>
  --
  subscription_id : UUID <<FK>>
  stripe_invoice_id : VARCHAR(100)
  amount : INTEGER
  currency : VARCHAR(3)
  status : VARCHAR(50)
  paid_at : TIMESTAMP
}

' Audit
entity AuditLog {
  *log_id : UUID <<PK>>
  --
  org_id : UUID <<FK>> nullable
  user_id : UUID <<FK>> nullable
  action : VARCHAR(100)
  resource_type : VARCHAR(50)
  resource_id : VARCHAR(100)
  ip_address : INET
  user_agent : TEXT
  timestamp : TIMESTAMP <<PARTITION KEY>>
}

' Relationships
User ||--o| UserPreference : has
User ||--o{ OrgMember : belongs_to
User ||--o{ Room : owns
User ||--o| Subscription : subscribes

Organization ||--o{ OrgMember : contains
Organization ||--o{ Room : restricts
Organization ||--|| Subscription : pays_via
Organization ||--o{ AuditLog : generates

Room ||--o{ RoomParticipant : hosts
Room ||--o{ Round : contains
Room ||--o{ SessionHistory : records

Round ||--o{ Vote : collects
RoomParticipant ||--o{ Vote : casts

Subscription ||--o{ PaymentHistory : has

User ||--o{ AuditLog : performs

@enduml
~~~
```

### Context: database-indexing-strategy (from 03_System_Structure_and_Data.md)

```markdown
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

### Context: data-model-overview (from 01_Plan_Overview_and_Setup.md)

```markdown
<!-- anchor: data-model-overview -->
*   **Data Model Overview:**

    **Core Entities (11 tables):**
    1. **User** - Registered user accounts (OAuth provider, subscription tier, profile)
    2. **UserPreference** - Saved defaults (deck type, theme, room settings)
    3. **Organization** - Enterprise workspaces (SSO config, branding)
    4. **OrgMember** - User-organization membership with roles
    5. **Room** - Estimation sessions (6-char ID, privacy mode, config JSONB)
    6. **RoomParticipant** - Active session participants (anonymous or authenticated)
    7. **Round** - Estimation rounds within session (story, votes, consensus)
    8. **Vote** - Individual card selections per round
    9. **SessionHistory** - Completed session records with summary stats
    10. **Subscription** - Stripe subscription records (tier, status, billing cycle)
    11. **PaymentHistory** - Payment transaction log
    12. **AuditLog** - Enterprise compliance trail (partitioned by month)

    **Design Principles:**
    *   Normalized core entities (3NF) for transactional consistency
    *   JSONB columns for flexible configuration (room settings, deck definitions)
    *   Soft deletes (`deleted_at`) for audit trail and GDPR compliance
    *   Partitioning for SessionHistory and AuditLog (monthly range partitions)

    **Key Diagram Planned:**
    *   Entity Relationship Diagram (PlantUML ERD) - Shows relationships and cardinality (Created in Architecture Blueprint reference)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code
*   **File:** `backend/src/main/resources/db/migration/V1__initial_schema.sql`
    *   **Summary:** Establishes all required ENUM types plus the full relational schema for User, Organization, Room, Round, Vote, SessionHistory, Subscription, PaymentHistory, AuditLog, and support tables, complete with cascading foreign keys, soft-delete columns, JSONB fields, and descriptive `COMMENT` statements.
    *   **Recommendation:** When extending or correcting schema objects, follow this file’s style—declare supporting types first, create tables in dependency order, and document intent with `COMMENT ON` blocks so Flyway history stays clear.
*   **File:** `backend/src/main/resources/db/migration/V2__create_partitions.sql`
    *   **Summary:** Pre-creates monthly range partitions for SessionHistory and AuditLog covering the current and next three months, plus in-file guidance for automating future partition creation via pg_partman or scheduled jobs.
    *   **Recommendation:** Keep monthly partition tables in sync with deployment timelines; if you add older or newer partitions, name them `table_YYYY_MM` and ensure ranges do not overlap to avoid insert failures.
*   **File:** `backend/src/main/resources/db/migration/V3__create_indexes.sql`
    *   **Summary:** Adds all high-priority and composite indexes enumerated in the architecture spec, including partial indexes for soft-deleted filtering and covering indexes (`INCLUDE`) for vote queries.
    *   **Recommendation:** Before introducing new indexes, confirm they align with the documented strategy and reuse the naming convention `idx_<table>_<purpose>` to keep maintenance scripts predictable.

### Implementation Tips & Notes
*   **Tip:** UUID primary keys rely on `gen_random_uuid()` (from `pgcrypto`)—ensure this extension is enabled in any new environments or add `CREATE EXTENSION IF NOT EXISTS pgcrypto;` as part of the baseline migration.
*   **Tip:** Partitioned tables (`session_history`, `audit_log`) use composite primary keys that include the partition key; when referencing them, always supply the partitioned timestamp column to keep query plans using partition pruning.
*   **Note:** Foreign keys are intentionally configured with cascading behaviors (`ON DELETE CASCADE`, `SET NULL`, or `RESTRICT`). Mirror those semantics when adding related tables so data lifecycle rules remain consistent.
*   **Warning:** Flyway executes migrations in lexical order. If you need to amend an earlier migration, create a new `Vx__` script instead of editing existing files to avoid checksum conflicts.
