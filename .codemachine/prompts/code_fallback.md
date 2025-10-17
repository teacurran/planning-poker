# Code Refinement Task

The previous code submission did not pass verification. You must fix the following issues and resubmit your work.

---

## Original Task Description

Create Flyway migration scripts for all 11 core entities: User, UserPreference, Organization, OrgMember, Room, RoomParticipant, Round, Vote, SessionHistory, Subscription, PaymentHistory, AuditLog. Define tables with proper column types (UUID primary keys, VARCHAR lengths, TIMESTAMP with timezone, JSONB for configurations), foreign key constraints, indexes (see indexing strategy in ERD section), and partitioning setup for SessionHistory and AuditLog (monthly range partitions). Include `deleted_at` timestamp for soft deletes on User and Room tables.

---

## Issues Detected

*   **CRITICAL SQL Syntax Error:** The `room_participant` table definition in `backend/src/main/resources/db/migration/V1__initial_schema.sql` (line 164) contains an invalid PRIMARY KEY constraint that uses a function expression: `PRIMARY KEY (room_id, COALESCE(user_id::TEXT, anonymous_id))`. PostgreSQL does NOT allow expressions or functions in PRIMARY KEY definitions - only direct column references.

    **Error message from PostgreSQL:**
    ```
    ERROR:  syntax error at or near "("
    LINE 9:     PRIMARY KEY (room_id, COALESCE(user_id::TEXT, anonymous_...
    ```

*   **Consequence:** Because the `room_participant` table failed to create, all subsequent tables and constraints that reference `room_participant` also failed. This is a complete migration failure.

---

## Best Approach to Fix

You MUST modify the `room_participant` table definition in `backend/src/main/resources/db/migration/V1__initial_schema.sql`.

**Solution:** Since PostgreSQL requires direct column references in PRIMARY KEY constraints, you need to use a different approach to handle the polymorphic participant_id (which can be either a user_id UUID or an anonymous_id string):

1. **Option 1 (Recommended):** Add a generated `participant_id` column as a computed/generated column or use a surrogate key approach:
   ```sql
   CREATE TABLE room_participant (
       participant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
       room_id VARCHAR(6) NOT NULL,
       user_id UUID,
       anonymous_id VARCHAR(50),
       display_name VARCHAR(100) NOT NULL,
       role room_role_enum NOT NULL DEFAULT 'VOTER',
       connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
       disconnected_at TIMESTAMP WITH TIME ZONE,
       CONSTRAINT fk_room_participant_room FOREIGN KEY (room_id)
           REFERENCES room(room_id) ON DELETE CASCADE,
       CONSTRAINT fk_room_participant_user FOREIGN KEY (user_id)
           REFERENCES "user"(user_id) ON DELETE SET NULL,
       CONSTRAINT chk_room_participant_identity CHECK (
           (user_id IS NOT NULL AND anonymous_id IS NULL) OR
           (user_id IS NULL AND anonymous_id IS NOT NULL)
       ),
       CONSTRAINT uq_room_participant_room_user UNIQUE (room_id, user_id) WHERE user_id IS NOT NULL,
       CONSTRAINT uq_room_participant_room_anon UNIQUE (room_id, anonymous_id) WHERE anonymous_id IS NOT NULL
   );
   ```
   This approach adds a surrogate UUID primary key and uses partial unique indexes to ensure that within each room, a user_id or anonymous_id can only appear once.

2. **Option 2:** Use separate columns with conditional uniqueness:
   ```sql
   CREATE TABLE room_participant (
       room_id VARCHAR(6) NOT NULL,
       user_id UUID,
       anonymous_id VARCHAR(50),
       display_name VARCHAR(100) NOT NULL,
       role room_role_enum NOT NULL DEFAULT 'VOTER',
       connected_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
       disconnected_at TIMESTAMP WITH TIME ZONE,
       PRIMARY KEY (room_id, user_id, anonymous_id),
       CONSTRAINT fk_room_participant_room FOREIGN KEY (room_id)
           REFERENCES room(room_id) ON DELETE CASCADE,
       CONSTRAINT fk_room_participant_user FOREIGN KEY (user_id)
           REFERENCES "user"(user_id) ON DELETE SET NULL,
       CONSTRAINT chk_room_participant_identity CHECK (
           (user_id IS NOT NULL AND anonymous_id IS NULL) OR
           (user_id IS NULL AND anonymous_id IS NOT NULL)
       )
   );
   ```
   However, this has the downside of allowing NULLs in the primary key for anonymous participants, which is non-standard.

**I recommend Option 1** because it:
- Uses a clean surrogate key pattern that's standard in database design
- Maintains data integrity with partial unique constraints
- Allows proper foreign key references from the `vote` table (which already references `participant_id` as TEXT)
- Avoids NULL values in primary key columns

**IMPORTANT:** After fixing the `room_participant` table, you MUST also update the `vote` table's `participant_id` column to match. If you use Option 1 with a UUID surrogate key, the vote table should reference this UUID directly instead of using TEXT:

```sql
CREATE TABLE vote (
    vote_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id UUID NOT NULL,
    participant_id UUID NOT NULL,  -- References room_participant.participant_id
    card_value VARCHAR(10) NOT NULL,
    voted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vote_round FOREIGN KEY (round_id)
        REFERENCES round(round_id) ON DELETE CASCADE,
    CONSTRAINT fk_vote_participant FOREIGN KEY (participant_id)
        REFERENCES room_participant(participant_id) ON DELETE CASCADE,
    CONSTRAINT uq_vote_participant UNIQUE (round_id, participant_id)
);
```

Note: You can remove the `room_id` column from the vote table since it's redundant (the participant_id foreign key already links to room through room_participant).

After making these changes, re-run the migration to ensure all tables are created successfully.
