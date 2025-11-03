# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T1",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Extend `VotingService` to persist session summary data. When round completes (reveal called), update SessionHistory record with round count, participants JSONB array, summary stats (total votes, consensus rate). Create `SessionHistoryService` for querying past sessions: `getUserSessions(userId, from, to)` (date range), `getSessionById(sessionId)`, `getRoomSessions(roomId)`. Aggregate statistics: total rounds, average consensus rate, most active participants. Use `SessionHistoryRepository`. Handle partitioned table queries (specify partition key in WHERE clause).",
  "agent_type_hint": "BackendAgent",
  "inputs": "SessionHistory entity from I1, Voting flow completion points, Partitioning strategy (monthly partitions)",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/room/SessionHistory.java",
    "backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java",
    "backend/src/main/java/com/scrumpoker/domain/room/VotingService.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java",
    "backend/src/main/java/com/scrumpoker/domain/room/VotingService.java"
  ],
  "deliverables": "SessionHistoryService with query methods (getUserSessions, getSessionById, getRoomSessions), VotingService.revealRound extended to update SessionHistory summary, Session summary statistics (total rounds, consensus rate, participants), Partitioned table query optimization (partition pruning)",
  "acceptance_criteria": "Completing round updates SessionHistory record with round count, getUserSessions returns user's past sessions within date range, Session statistics correctly aggregated (consensus rate calculation), Queries use partition pruning (verify EXPLAIN plan), SessionHistory JSONB fields (participants, summary_stats) populated correctly",
  "dependencies": ["I4.T3"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: session-history-entity (from 03_System_Structure_and_Data.md)

The SessionHistory entity is designed as a partitioned table for performance:

```markdown
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
```

**Key Design Notes:**
- **Partitioned by month** using `started_at` as the partition key
- **JSONB fields** for flexible storage:
  - `participants`: Array of participant snapshots (display_name, role, vote_count)
  - `summary_stats`: Aggregate metrics (avg_estimation_time, consensus_rate, total_votes)
- **Composite Primary Key**: SessionHistoryId contains both `session_id` and `started_at` to support partitioning
- **Immutable records**: Session history is write-once, never updated after creation

### Context: reporting-requirements (from 01_Context_and_Drivers.md)

```markdown
#### Reporting Requirements
- **Free Tier:** Basic session summaries (story count, consensus rate, average vote)
- **Pro Tier:** Round-level detail, user consistency metrics, CSV/JSON/PDF export
- **Enterprise Tier:** Organizational dashboards, team trends, SSO-filtered reports, audit logs
```

### Context: iteration-6-task-1 (from 02_Iteration_I6.md)

```markdown
**Task 6.1: Implement Session History Tracking**
*   **Task ID:** `I6.T1`
*   **Description:** Extend `VotingService` to persist session summary data. When round completes (reveal called), update SessionHistory record with round count, participants JSONB array, summary stats (total votes, consensus rate). Create `SessionHistoryService` for querying past sessions: `getUserSessions(userId, from, to)` (date range), `getSessionById(sessionId)`, `getRoomSessions(roomId)`. Aggregate statistics: total rounds, average consensus rate, most active participants. Use `SessionHistoryRepository`. Handle partitioned table queries (specify partition key in WHERE clause).

**Deliverables:**
- SessionHistoryService with query methods (getUserSessions, getSessionById, getRoomSessions)
- VotingService.revealRound extended to update SessionHistory summary
- Session summary statistics (total rounds, consensus rate, participants)
- Partitioned table query optimization (partition pruning)

**Acceptance Criteria:**
- Completing round updates SessionHistory record with round count
- getUserSessions returns user's past sessions within date range
- Session statistics correctly aggregated (consensus rate calculation)
- Queries use partition pruning (verify EXPLAIN plan)
- SessionHistory JSONB fields (participants, summary_stats) populated correctly
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/SessionHistory.java`
    *   **Summary:** This entity uses a composite primary key (`SessionHistoryId`) with `sessionId` (UUID) and `startedAt` (Instant) to support monthly partitioning. It has two critical JSONB fields stored as `String`: `participants` and `summaryStats`.
    *   **Recommendation:** You MUST manually serialize/deserialize JSONB fields using Jackson `ObjectMapper`. The fields are stored as plain String in the entity but represent JSON data structures.
    *   **Note:** The entity uses `@EmbeddedId` with `SessionHistoryId` composite key. When querying, you MUST reference `id.sessionId` and `id.startedAt` for the partition key.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java`
    *   **Summary:** This repository already contains useful query methods including `findByRoomId()`, `findByDateRange()`, `findRecentByRoomId()`, and `countByRoomId()`.
    *   **Recommendation:** You SHOULD reuse existing repository methods. Note that date range queries already optimize for partition pruning by filtering on `id.startedAt`.
    *   **Important:** The partition key is `id.startedAt` - all queries SHOULD include this field in WHERE clauses for optimal performance.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** The `revealRound()` method at line 153-183 currently calculates statistics (average, median, consensus) and updates the Round entity. This is where you need to add SessionHistory tracking.
    *   **Recommendation:** You MUST extend the `revealRound()` method to ALSO persist or update a SessionHistory record. The method already fetches all votes and calculates consensus using `ConsensusCalculator`.
    *   **Critical Pattern:** This service uses reactive Mutiny patterns (`Uni`, `Multi`). All your additions MUST use `Uni` return types and chain operations with `.onItem().transformToUni()` or `.call()`.
    *   **Note:** The method currently uses `@WithTransaction` annotation (line 153). Your SessionHistory update logic will be part of the same transaction.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/ConsensusCalculator.java`
    *   **Summary:** This utility class provides static methods for calculating statistics: `calculateConsensus()`, `calculateAverage()`, `calculateMedian()`, and private `calculateVariance()`.
    *   **Recommendation:** You SHOULD reuse these calculation methods for computing SessionHistory summary stats. The consensus algorithm uses variance threshold < 2.0.
    *   **Tip:** Consensus rate across multiple rounds = (rounds with consensus / total rounds). You'll need to track this when aggregating session data.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomParticipant.java`
    *   **Summary:** This entity represents both authenticated users (`user` field) and anonymous participants (`anonymousId` field). It has `displayName`, `role` (HOST/VOTER/OBSERVER), and connection timestamps.
    *   **Recommendation:** When building the participants JSONB array for SessionHistory, you MUST include relevant participant data from this entity. Suggested fields: participantId, displayName, role, vote_count (calculated from votes).

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
    *   **Summary:** Each vote has a reference to `round` and `participant`. The `cardValue` field stores the estimation value (e.g., "5", "?", "∞").
    *   **Recommendation:** You'll need to aggregate votes by participant to calculate each participant's vote count and consistency metrics for the SessionHistory summary.

### Implementation Tips & Notes

*   **Tip: JSONB Serialization Pattern**
    - I've confirmed that the SessionHistory entity stores JSONB as plain `String` fields.
    - You MUST use Jackson `ObjectMapper` to serialize Java objects to JSON strings before persisting.
    - Create POJO classes for the JSONB structures (e.g., `ParticipantSummary`, `SessionSummaryStats`) and serialize them with:
      ```java
      ObjectMapper mapper = new ObjectMapper();
      String participantsJson = mapper.writeValueAsString(participantsList);
      ```

*   **Tip: Session ID Management**
    - A "session" represents a single estimation meeting that may span multiple rounds.
    - You'll need to decide when to create a NEW SessionHistory record vs. UPDATE an existing one.
    - **Recommendation:** Create a SessionHistory record when the FIRST round in a room starts, then UPDATE it as each round completes.
    - Track the session ID in the Room entity or use a "current session" pattern.

*   **Warning: Partition Key Queries**
    - PostgreSQL partition pruning ONLY works if queries include the partition key (`id.startedAt`) in the WHERE clause.
    - When implementing `getUserSessions()`, you MUST filter by date range to ensure partition pruning.
    - Example: `find("room.owner.userId = ?1 and id.startedAt >= ?2 and id.startedAt <= ?3", userId, from, to)`

*   **Note: Consensus Rate Calculation**
    - Consensus rate = (number of rounds with `consensusReached = true`) / (total rounds)
    - You'll need to query all rounds for a session to calculate this when updating SessionHistory.
    - Store this as a decimal value (e.g., 0.75 for 75%) in the `summary_stats` JSONB field.

*   **Note: Service Dependencies**
    - You need to CREATE a new service: `SessionHistoryService` in `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
    - This is NOT a modification of an existing file - it's a NEW file.
    - Follow the same reactive patterns as other services (use `@ApplicationScoped`, inject repositories, return `Uni` types).

*   **Important: Reactive Transaction Boundaries**
    - VotingService already uses `@WithTransaction` on `revealRound()`.
    - Your SessionHistory persistence logic MUST be chained into the same reactive pipeline to participate in the transaction.
    - Use `.onItem().call()` to add side-effect operations (like updating SessionHistory) that return `Uni<Void>`.

*   **Warning: Testing Considerations**
    - The acceptance criteria requires verifying partition pruning with EXPLAIN plans.
    - You'll need to add repository tests that execute queries and check PostgreSQL query plans.
    - Use native queries with EXPLAIN ANALYZE to verify partition pruning is working.

### Suggested Implementation Order

1. **Create POJO classes** for JSONB structures (`ParticipantSummary`, `SessionSummaryStats`)
2. **Create SessionHistoryService** with query methods (getUserSessions, getSessionById, getRoomSessions)
3. **Extend VotingService.revealRound()** to update SessionHistory after calculating round statistics
4. **Add session tracking logic** (create session on first round, update on subsequent rounds)
5. **Implement JSONB serialization** using Jackson ObjectMapper
6. **Add unit tests** for SessionHistoryService
7. **Add integration tests** verifying partition pruning behavior

---

## 4. Critical Requirements Summary

**MUST DO:**
- ✅ Create new `SessionHistoryService` class with reactive methods
- ✅ Extend `VotingService.revealRound()` to update SessionHistory
- ✅ Use Jackson ObjectMapper for JSONB serialization
- ✅ Include partition key (`id.startedAt`) in all queries
- ✅ Calculate consensus rate across all rounds in a session
- ✅ Chain SessionHistory updates into VotingService transaction

**MUST NOT DO:**
- ❌ Do NOT use JPA converters for JSONB - manually serialize with ObjectMapper
- ❌ Do NOT query SessionHistory without date range filtering (breaks partition pruning)
- ❌ Do NOT block reactive chains - all operations must return Uni/Multi
- ❌ Do NOT create separate transactions - reuse VotingService transaction

**VALIDATE:**
- 🔍 Verify partition pruning with EXPLAIN ANALYZE queries
- 🔍 Test JSONB round-trip (serialize → persist → fetch → deserialize)
- 🔍 Confirm consensus rate calculation matches ConsensusCalculator algorithm
- 🔍 Ensure date range queries work across partition boundaries
