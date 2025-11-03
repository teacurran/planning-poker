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

### Context: Database Partitioning Strategy (from 03_System_Structure_and_Data.md)

```markdown
**Partitioning:**
- `SessionHistory` partitioned by `started_at` (monthly range partitions)
- `AuditLog` partitioned by `timestamp` (monthly range partitions)
- Automated partition creation via scheduled job or pg_partman extension
```

### Context: SessionHistory Database Schema (from V1__initial_schema.sql)

```sql
-- SessionHistory: Completed session record (partitioned table)
CREATE TABLE session_history (
    session_id UUID DEFAULT gen_random_uuid(),
    room_id VARCHAR(6) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    total_rounds INTEGER NOT NULL,
    total_stories INTEGER NOT NULL,
    participants JSONB NOT NULL,
    summary_stats JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, started_at),
    CONSTRAINT fk_session_room FOREIGN KEY (room_id)
        REFERENCES room(room_id) ON DELETE CASCADE
) PARTITION BY RANGE (started_at);

COMMENT ON TABLE session_history IS 'Completed session records partitioned by month for performance';
COMMENT ON COLUMN session_history.participants IS 'JSONB array of participant snapshots';
COMMENT ON COLUMN session_history.summary_stats IS 'JSONB: avg_estimation_time, consensus_rate, total_votes';
```

### Context: Database Indexing Strategy (from 03_System_Structure_and_Data.md)

```markdown
**High-Priority Indexes:**
- `SessionHistory(started_at)` - Partition pruning for date-range queries

**Partitioning:**
- `SessionHistory` partitioned by `started_at` (monthly range partitions)
- `AuditLog` partitioned by `timestamp` (monthly range partitions)
- Automated partition creation via scheduled job or pg_partman extension
```

### Context: Task I6.T1 Detailed Requirements (from 02_Iteration_I6.md)

```markdown
**Task 6.1: Implement Session History Tracking**
*   **Task ID:** `I6.T1`
*   **Description:** Extend `VotingService` to persist session summary data. When round completes (reveal called), update SessionHistory record with round count, participants JSONB array, summary stats (total votes, consensus rate). Create `SessionHistoryService` for querying past sessions: `getUserSessions(userId, from, to)` (date range), `getSessionById(sessionId)`, `getRoomSessions(roomId)`. Aggregate statistics: total rounds, average consensus rate, most active participants. Use `SessionHistoryRepository`. Handle partitioned table queries (specify partition key in WHERE clause).
*   **Agent Type Hint:** `BackendAgent`
*   **Inputs:**
    *   SessionHistory entity from I1
    *   Voting flow completion points
    *   Partitioning strategy (monthly partitions)
*   **Input Files:**
    *   `backend/src/main/java/com/scrumpoker/domain/room/SessionHistory.java`
    *   `backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java`
    *   `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
*   **Target Files:**
    *   `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
    *   `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java` (extend reveal method)
*   **Deliverables:**
    *   SessionHistoryService with query methods (getUserSessions, getSessionById, getRoomSessions)
    *   VotingService.revealRound extended to update SessionHistory summary
    *   Session summary statistics (total rounds, consensus rate, participants)
    *   Partitioned table query optimization (partition pruning)
*   **Acceptance Criteria:**
    *   Completing round updates SessionHistory record with round count
    *   getUserSessions returns user's past sessions within date range
    *   Session statistics correctly aggregated (consensus rate calculation)
    *   Queries use partition pruning (verify EXPLAIN plan)
    *   SessionHistory JSONB fields (participants, summary_stats) populated correctly
*   **Dependencies:** [I4.T3]
*   **Parallelizable:** No (depends on VotingService)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/VotingService.java`
    *   **Summary:** This is the CORE domain service managing the complete voting lifecycle. It already contains a COMPLETE implementation of session history tracking (lines 357-601). The methods `updateSessionHistory()`, `createNewSessionHistory()`, `updateExistingSessionHistory()`, `buildParticipantSummaries()`, and `buildSummaryStats()` are ALREADY IMPLEMENTED.
    *   **CRITICAL DISCOVERY:** The task description says to "extend VotingService to persist session summary data" but this functionality ALREADY EXISTS. The `revealRound()` method (line 171) already calls `updateSessionHistory()` at line 199. This is COMPLETE AND WORKING.
    *   **Recommendation:** You MUST NOT re-implement the session history tracking in VotingService. It's already done. Your task is to create the NEW `SessionHistoryService` class for QUERYING existing session data, NOT for creating it.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
    *   **Summary:** This service ALREADY EXISTS and provides comprehensive querying capabilities for session history with partition optimization.
    *   **Existing Methods:**
        - `getUserSessions(UUID userId, Instant from, Instant to)` - Returns user's sessions in date range (lines 46-62)
        - `getSessionById(UUID sessionId)` - Returns single session by ID (lines 72-81)
        - `getSessionByIdAndDate(UUID sessionId, Instant startedAt)` - Partition-optimized lookup (lines 91-99)
        - `getRoomSessions(String roomId)` - Returns all sessions for a room (lines 108-115)
        - `getRoomSessionsByDateRange(String roomId, Instant from, Instant to)` - Partition-optimized room sessions (lines 125-141)
        - `getUserStatistics(UUID userId, Instant from, Instant to)` - Aggregate user statistics (lines 158-232)
        - `getRoomStatistics(String roomId)` - Aggregate room statistics (lines 240-287)
    *   **CRITICAL DISCOVERY:** This service is ALREADY FULLY IMPLEMENTED with all required methods. It correctly uses partition pruning by including `id.startedAt` in WHERE clauses.
    *   **Recommendation:** This task appears to be ALREADY COMPLETE. The SessionHistoryService exists and provides all the functionality described in the task requirements.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/SessionHistory.java`
    *   **Summary:** JPA entity for SessionHistory with composite key (sessionId + startedAt) for partition support. Uses JSONB columns for `participants` (String) and `summaryStats` (String).
    *   **Recommendation:** You MUST use the existing `SessionHistoryId` composite key class when querying. The entity uses `@EmbeddedId` for the composite key.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/SessionHistoryRepository.java`
    *   **Summary:** Panache repository with comprehensive finder methods including partition-aware queries.
    *   **Existing Methods:**
        - `findByRoomId(String roomId)` - Find sessions by room
        - `findByDateRange(Instant startDate, Instant endDate)` - Partition-optimized date range query
        - `findBySessionId(UUID sessionId)` - Find by session ID
        - `findRecentByRoomId(String roomId, Instant since)` - Recent sessions
        - `countByRoomId(String roomId)` - Count sessions
        - `findByMinRounds(Integer minRounds)` - Analytics query
    *   **Recommendation:** You SHOULD use these existing repository methods from SessionHistoryService instead of writing raw JPQL queries.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ParticipantSummary.java`
    *   **Summary:** POJO for participant data stored in SessionHistory JSONB field. Has proper Jackson annotations (`@JsonProperty`) for snake_case JSON serialization.
    *   **Recommendation:** You MUST use this existing class for serializing participant data to JSONB. Do not create a new DTO.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionSummaryStats.java`
    *   **Summary:** POJO for summary statistics stored in SessionHistory JSONB field. Contains: totalVotes, consensusRate, avgEstimationTimeSeconds, roundsWithConsensus.
    *   **Recommendation:** You MUST use this existing class for serializing summary stats to JSONB. Do not create a new DTO.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoundRepository.java`
    *   **Summary:** Repository providing query methods for rounds, including `findRevealedByRoomId()` which is used by VotingService to build session history.
    *   **Recommendation:** The VotingService already uses this repository correctly. No changes needed.

### Implementation Tips & Notes

*   **CRITICAL:** This task appears to be ALREADY COMPLETE. Both the VotingService extension (session history tracking) and SessionHistoryService (querying) are fully implemented and working.

*   **Verification Strategy:** Before making any changes, you SHOULD:
    1. Read the existing `SessionHistoryService.java` file completely (it's at line 1-289)
    2. Read the `VotingService.java` file focusing on the `updateSessionHistory()` method (lines 357-601)
    3. Confirm that all deliverables from the task specification are already present
    4. Run existing tests to verify the functionality works

*   **Partition Pruning:** The existing queries correctly include `id.startedAt` in WHERE clauses (e.g., line 59 in SessionHistoryService: `"room.owner.userId = ?1 and id.startedAt >= ?2 and id.startedAt <= ?3"`). This ensures PostgreSQL can prune partitions efficiently.

*   **JSONB Serialization Pattern:** The VotingService uses Jackson ObjectMapper to serialize POJOs to JSON strings before storing in JSONB columns (lines 443, 447, 487, 491). The SessionHistoryService deserializes them back using `objectMapper.readValue()` (lines 180-187, 199-208, 262-271).

*   **Session Definition:** A "session" is defined as continuous estimation activity in a room. The VotingService creates ONE SessionHistory record when the FIRST round is revealed, then UPDATES that same record when subsequent rounds are revealed in the same room (lines 378-413).

*   **Reactive Patterns:** All methods use Smallrye Mutiny `Uni<>` and `Multi<>` for reactive, non-blocking I/O. The VotingService uses `Uni.combine().all().unis()` for parallel queries and complex reactive chains.

*   **Testing Considerations:** The existing SessionHistoryRepository test at `backend/src/test/java/com/scrumpoker/repository/SessionHistoryRepositoryTest.java` should verify partition pruning and JSONB operations. You may need to verify these tests exist and pass.

*   **WARNING:** Do NOT duplicate the session history tracking logic that already exists in VotingService. The task description may be outdated or this work was already completed in a previous iteration.

*   **Next Steps Recommendation:**
    1. Verify the existing implementation meets all acceptance criteria
    2. Run tests: `mvn test -Dtest=SessionHistoryRepositoryTest` and `mvn test -Dtest=*Voting*`
    3. If tests pass and functionality is complete, mark the task as done
    4. If there are gaps, identify the specific missing pieces and implement only those
    5. Do NOT rewrite existing working code

*   **Code Quality Note:** The existing implementation is well-structured, follows Quarkus/Panache best practices, uses proper error handling, and includes comprehensive JavaDoc comments. Any additions should match this quality level.

