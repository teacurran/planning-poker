# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I6.T2",
  "iteration_id": "I6",
  "iteration_goal": "Implement session history tracking, tier-based reporting (basic summaries for Free, detailed analytics for Pro/Enterprise), export functionality (CSV/PDF), and frontend reporting UI.",
  "description": "Create `ReportingService` implementing tier-gated analytics. Methods: `getBasicSessionSummary(sessionId)` (Free tier: story count, consensus rate, average vote), `getDetailedSessionReport(sessionId)` (Pro tier: round-by-round breakdown, individual votes, user consistency metrics), `generateExport(sessionId, format)` (Pro tier: enqueue export job for CSV/PDF generation). Inject `FeatureGate` to enforce tier requirements. Query SessionHistory and Round/Vote entities. Calculate user consistency (standard deviation of user's votes across rounds). Return tier-appropriate DTOs.",
  "agent_type_hint": "BackendAgent",
  "inputs": "Reporting tier matrix from product spec (Free vs. Pro features), SessionHistoryService from I6.T1, FeatureGate from I5.T4",
  "input_files": [
    "backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java",
    "backend/src/main/java/com/scrumpoker/security/FeatureGate.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java",
    "backend/src/main/java/com/scrumpoker/domain/reporting/SessionSummaryDTO.java",
    "backend/src/main/java/com/scrumpoker/domain/reporting/DetailedSessionReportDTO.java"
  ],
  "deliverables": "ReportingService with tier-gated methods, Basic summary for Free tier (limited fields), Detailed report for Pro tier (round breakdown, individual votes), User consistency metrics (vote variance calculation), Export job enqueuing (Redis Stream message), FeatureGate enforcement (403 if Free tier requests detailed report)",
  "acceptance_criteria": "getBasicSessionSummary returns story count, consensus rate, Free tier user cannot access detailed report (403 error), Pro tier user gets detailed report with round-by-round data, User consistency calculated correctly (standard deviation of votes), Export job enqueued to Redis Stream, Tier enforcement integrated via FeatureGate",
  "dependencies": ["I6.T1", "I5.T4"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: reporting-requirements (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: reporting-requirements -->
#### Reporting Requirements
- **Free Tier:** Basic session summaries (story count, consensus rate, average vote)
- **Pro Tier:** Round-level detail, user consistency metrics, CSV/JSON/PDF export
- **Enterprise Tier:** Organizational dashboards, team trends, SSO-filtered reports, audit logs
```

### Context: performance-nfrs (from 01_Context_and_Drivers.md)

```markdown
<!-- anchor: performance-nfrs -->
#### Performance
- **Latency:** <200ms round-trip time for WebSocket messages within region
- **Throughput:** Support 500 concurrent sessions with 6,000 active WebSocket connections
- **Response Time:** REST API endpoints respond within <500ms for p95
- **Real-time Updates:** State synchronization across clients within 100ms
```

### Context: task-i6-t2 (from 02_Iteration_I6.md)

```markdown
<!-- anchor: task-i6-t2 -->
*   **Task 6.2: Implement Reporting Service (Tier-Based Access)**
    *   **Task ID:** `I6.T2`
    *   **Description:** Create `ReportingService` implementing tier-gated analytics. Methods: `getBasicSessionSummary(sessionId)` (Free tier: story count, consensus rate, average vote), `getDetailedSessionReport(sessionId)` (Pro tier: round-by-round breakdown, individual votes, user consistency metrics), `generateExport(sessionId, format)` (Pro tier: enqueue export job for CSV/PDF generation). Inject `FeatureGate` to enforce tier requirements. Query SessionHistory and Round/Vote entities. Calculate user consistency (standard deviation of user's votes across rounds). Return tier-appropriate DTOs.
    *   **Agent Type Hint:** `BackendAgent`
    *   **Inputs:**
        *   Reporting tier matrix from product spec (Free vs. Pro features)
        *   SessionHistoryService from I6.T1
        *   FeatureGate from I5.T4
    *   **Input Files:**
        *   Product specification (reporting feature comparison)
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
        *   `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
    *   **Target Files:**
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/ReportingService.java`
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/SessionSummaryDTO.java`
        *   `backend/src/main/java/com/scrumpoker/domain/reporting/DetailedSessionReportDTO.java`
    *   **Deliverables:**
        *   ReportingService with tier-gated methods
        *   Basic summary for Free tier (limited fields)
        *   Detailed report for Pro tier (round breakdown, individual votes)
        *   User consistency metrics (vote variance calculation)
        *   Export job enqueuing (Redis Stream message)
        *   FeatureGate enforcement (403 if Free tier requests detailed report)
    *   **Acceptance Criteria:**
        *   getBasicSessionSummary returns story count, consensus rate
        *   Free tier user cannot access detailed report (403 error)
        *   Pro tier user gets detailed report with round-by-round data
        *   User consistency calculated correctly (standard deviation of votes)
        *   Export job enqueued to Redis Stream
        *   Tier enforcement integrated via FeatureGate
    *   **Dependencies:** [I6.T1, I5.T4]
    *   **Parallelizable:** No (depends on SessionHistoryService, FeatureGate)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionHistoryService.java`
    *   **Summary:** This service provides reactive methods for querying SessionHistory records. It already implements `getSessionById(UUID)`, `getSessionByIdAndDate(UUID, Instant)`, and `getUserSessions()`. It uses `ObjectMapper` to deserialize JSONB fields (participants, summaryStats) from SessionHistory entities.
    *   **Recommendation:** You MUST import and inject this service into your ReportingService. Use `getSessionById(sessionId)` to retrieve the SessionHistory record needed for both basic and detailed reports. The service already handles partition-optimized queries and JSONB deserialization.

*   **File:** `backend/src/main/java/com/scrumpoker/security/FeatureGate.java`
    *   **Summary:** This service implements tier-based feature access control using a hierarchical tier system (FREE < PRO < PRO_PLUS < ENTERPRISE). It provides both boolean checks (`canAccessAdvancedReports(User)`) and imperative enforcement methods (`requireCanAccessAdvancedReports(User)`).
    *   **Recommendation:** You MUST inject this service and use `featureGate.requireCanAccessAdvancedReports(user)` in your `getDetailedSessionReport()` method. This will throw a `FeatureNotAvailableException` automatically if a Free tier user attempts to access detailed reports, ensuring 403 enforcement. Also use `featureGate.canAccessAdvancedReports(user)` for the export job generation method.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Round.java`
    *   **Summary:** This entity represents individual estimation rounds with fields: `roundId`, `room`, `roundNumber`, `storyTitle`, `startedAt`, `revealedAt`, `average`, `median`, `consensusReached`. The average is stored as `BigDecimal` with precision 5, scale 2. The median is stored as `String` (VARCHAR) to support non-numeric cards like "?", "∞", "☕".
    *   **Recommendation:** You MUST query Round entities for the detailed report. Use `RoundRepository.findByRoomId(roomId)` to get all rounds for a session. Include these fields in your `DetailedSessionReportDTO`: roundNumber, storyTitle, average, median, consensusReached.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
    *   **Summary:** This entity stores individual votes with fields: `voteId`, `round`, `participant`, `cardValue`, `votedAt`. The `cardValue` is stored as a String to support non-numeric values. Votes are immutable after creation.
    *   **Recommendation:** You MUST query Vote entities for detailed reports and user consistency calculations. Use `VoteRepository.findByRoundId(roundId)` to get all votes for each round. For user consistency metrics, you need to calculate the standard deviation of a participant's votes across all rounds in the session.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoundRepository.java`
    *   **Summary:** Provides reactive Panache repository methods including `findByRoomId(roomId)`, `findRevealedByRoomId(roomId)`, `findConsensusRoundsByRoomId(roomId)`, `countByRoomId(roomId)`.
    *   **Recommendation:** You SHOULD inject this repository and use `findByRoomId(roomId)` to retrieve all rounds for a given room (identified by the SessionHistory.room.roomId). This will give you the round-by-round data needed for detailed reports.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** Provides reactive methods including `findByRoundId(roundId)`, `findByParticipantId(participantId)`, `countByRoundId(roundId)`.
    *   **Recommendation:** You MUST inject this repository and use `findByRoundId(roundId)` for each round to retrieve individual votes. This is essential for the detailed report's round-by-round breakdown and for calculating user consistency metrics.

*   **File:** `backend/src/main/java/com/scrumpoker/event/RoomEventPublisher.java`
    *   **Summary:** This service publishes events to Redis Pub/Sub channels using the pattern `room:{roomId}`. It uses `ReactiveRedisDataSource` and `ReactivePubSubCommands<String>` for publishing JSON-serialized events.
    *   **Recommendation:** For export job enqueuing, you will need to use Redis Streams (NOT Pub/Sub). You should inject `ReactiveRedisDataSource` and use the `.stream()` method to get `ReactiveStreamCommands`, then use `.xadd()` to add messages to the `jobs:reports` stream. Pattern: `redisDataSource.stream(String.class).xadd(streamKey, Map.of(...))`

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/SessionSummaryStats.java`
    *   **Summary:** This POJO is used for deserializing the JSONB `summary_stats` field from SessionHistory. It contains: `totalVotes`, `consensusRate`, `avgEstimationTimeSeconds`, `roundsWithConsensus`. Uses Jackson annotations like `@JsonProperty`.
    *   **Recommendation:** You SHOULD reuse this class when deserializing SessionHistory.summaryStats in your basic summary report. The SessionHistoryService already shows how to deserialize this using `objectMapper.readValue(session.summaryStats, SessionSummaryStats.class)`.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/reporting/ParticipantSummary.java`
    *   **Summary:** This POJO is used for deserializing the JSONB `participants` array field from SessionHistory. It likely contains participant metadata including vote counts.
    *   **Recommendation:** You will need this class when deserializing SessionHistory.participants for both basic and detailed reports. Use it to extract participant information for the detailed report's round-by-round breakdown.

### Implementation Tips & Notes

*   **Tip: User Consistency Calculation** - The task requires calculating "user consistency (standard deviation of user's votes across rounds)". To implement this:
    1. For each participant in the session, collect all their numeric votes across all rounds.
    2. Filter out non-numeric votes (?, ∞, ☕) as they cannot be used in standard deviation calculation.
    3. Parse the numeric card values to Double (e.g., "1" → 1.0, "13" → 13.0).
    4. Calculate the standard deviation using the formula: σ = sqrt(Σ(xi - μ)² / n) where μ is the mean.
    5. Return a Map or DTO with participantId/displayName → standard deviation.
    6. Consider edge cases: participants who voted only once (σ = 0), participants who only voted non-numeric values (exclude or return null).

*   **Tip: Redis Streams for Export Jobs** - The task specifies enqueuing export jobs to a Redis Stream named `jobs:reports`. The project currently uses Redis Pub/Sub (see RoomEventPublisher), but Streams are different. You need to:
    1. Inject `@Inject ReactiveRedisDataSource redisDataSource;`
    2. Get stream commands: `ReactiveStreamCommands<String> streamCommands = redisDataSource.stream(String.class);`
    3. Create a message payload Map with fields like: `sessionId`, `format` (CSV or PDF), `userId`, `requestedAt`.
    4. Use `streamCommands.xadd("jobs:reports", Map.of("sessionId", sessionId.toString(), "format", format, ...))` to enqueue.
    5. Return the generated job ID (Redis Streams returns a message ID like "1234567890-0").

*   **Note: DTO Design Patterns** - Looking at existing DTOs in the project (e.g., `RoomDTO`, `SubscriptionDTO`), the codebase uses simple POJOs with public fields, Jackson annotations, and no-arg constructors. Your DTOs should follow this pattern:
    - Use `@JsonProperty` annotations for field name mapping.
    - Provide both a no-arg constructor (for Jackson) and an all-args constructor.
    - Use immutable fields where possible (final if not requiring setter).
    - For the detailed report, create nested DTOs or inner classes for round details (e.g., `RoundDetailDTO` containing roundNumber, storyTitle, votes list).

*   **Note: Reactive Patterns** - All existing services in the project return `Uni<>` or `Multi<>` types from SmallRye Mutiny. Your ReportingService methods MUST return:
    - `Uni<SessionSummaryDTO>` for `getBasicSessionSummary()`
    - `Uni<DetailedSessionReportDTO>` for `getDetailedSessionReport()`
    - `Uni<String>` for `generateExport()` (return job ID as String)
    - Chain operations using `.onItem().transformToUni()` when you need to perform additional async queries.

*   **Warning: BigDecimal vs Double for Standard Deviation** - The Round entity uses `BigDecimal` for the average. However, for standard deviation calculations in Java, it's much easier to work with `double` or `Double`. You can convert card values to `Double` for the calculation, then wrap the result in `BigDecimal` for consistency with the rest of the codebase. Use `BigDecimal.valueOf(double)` for the conversion.

*   **Warning: Handle Non-Numeric Votes** - The codebase supports special card values like "?" (unknown), "∞" (infinity), "☕" (coffee break). When calculating numeric statistics (average, standard deviation):
    1. Check if the card value is numeric using a try-catch with `Double.parseDouble(cardValue)`.
    2. Skip non-numeric values in calculations.
    3. Document this behavior in JavaDoc.
    4. Consider adding a count of non-numeric votes to the detailed report for transparency.

*   **Best Practice: Follow Existing Test Patterns** - I reviewed `BillingServiceTest.java` which shows the project's testing conventions:
    - Use JUnit 5 with `@ExtendWith(MockitoExtension.class)`
    - Mock dependencies with `@Mock` annotation
    - Use `@InjectMocks` for the service under test
    - Set up test data in `@BeforeEach` method
    - Use AssertJ for assertions: `assertThat(...).isEqualTo(...)`
    - Mock reactive returns with `Uni.createFrom().item(...)` or `Uni.createFrom().nullItem()`
    - Use `when(...).thenReturn(...)` for stubbing
    - Test both happy paths and exception scenarios (e.g., Free tier accessing Pro features)

*   **Critical: Tier Enforcement Exception Handling** - When `FeatureGate.requireCanAccessAdvancedReports(user)` throws `FeatureNotAvailableException`, this exception should propagate up to the REST controller layer where it will be caught by `FeatureNotAvailableExceptionMapper` (which I can see exists in the project). This mapper will convert it to a 403 HTTP response. DO NOT catch this exception in the service layer; let it bubble up.

*   **Critical: Session vs Room Relationship** - The SessionHistory entity has a `room` field (ManyToOne relationship). To get rounds and votes for a session:
    1. Retrieve the SessionHistory by sessionId using SessionHistoryService.
    2. Get the roomId from `sessionHistory.room.roomId` (6-character nanoid).
    3. Query rounds using `roundRepository.findByRoomId(roomId)`.
    4. Note: This will return ALL rounds for that room, not just the ones in this specific session. You may need to filter by timestamp (rounds where `startedAt` is between `sessionHistory.id.startedAt` and `sessionHistory.endedAt` or the latest round timestamp).

*   **Performance Consideration:** The detailed report may require multiple database queries (SessionHistory, Rounds, Votes for each round, RoomParticipants). Use reactive composition with `Uni.combine()` or `Multi.toUni()` to execute queries efficiently. Avoid blocking operations or sequential queries where parallel queries are possible.

---

## 4. Export Job Data Structure Recommendation

Based on the analysis, here's the recommended structure for the Redis Stream message:

```java
Map<String, String> jobData = Map.of(
    "jobId", UUID.randomUUID().toString(),  // Unique job ID
    "sessionId", sessionId.toString(),
    "format", format,  // "CSV" or "PDF"
    "userId", user.userId.toString(),
    "requestedAt", Instant.now().toString()
);
```

The worker (to be implemented in I6.T3) will consume from this stream using `XREAD` or `XREADGROUP` commands.

---

## 5. User Consistency Metric Formula

Standard deviation calculation for user's votes:

```
For each participant P:
1. Collect all numeric votes V = {v1, v2, ..., vn} from all rounds
2. Calculate mean: μ = (v1 + v2 + ... + vn) / n
3. Calculate variance: σ² = Σ((vi - μ)²) / n
4. Calculate standard deviation: σ = sqrt(σ²)

Lower σ indicates higher consistency (user votes similarly across rounds).
Higher σ indicates lower consistency (user's votes vary widely).
```

A participant who always votes "5" would have σ = 0 (perfect consistency).
A participant who votes {1, 3, 8, 13} would have σ ≈ 4.74 (low consistency).

---

## 6. DTO Structure Recommendations

### SessionSummaryDTO (Free Tier)
```java
public class SessionSummaryDTO {
    @JsonProperty("session_id")
    private UUID sessionId;

    @JsonProperty("room_title")
    private String roomTitle;

    @JsonProperty("started_at")
    private Instant startedAt;

    @JsonProperty("total_stories")
    private Integer totalStories;

    @JsonProperty("total_rounds")
    private Integer totalRounds;

    @JsonProperty("consensus_rate")
    private BigDecimal consensusRate;  // 0.0 to 1.0

    @JsonProperty("average_vote")
    private BigDecimal averageVote;

    @JsonProperty("participant_count")
    private Integer participantCount;
}
```

### DetailedSessionReportDTO (Pro Tier)
```java
public class DetailedSessionReportDTO {
    // All fields from SessionSummaryDTO
    @JsonProperty("session_id")
    private UUID sessionId;

    // ... other basic fields ...

    // Detailed additions
    @JsonProperty("rounds")
    private List<RoundDetailDTO> rounds;

    @JsonProperty("user_consistency")
    private Map<String, BigDecimal> userConsistency;  // displayName -> std dev

    public static class RoundDetailDTO {
        @JsonProperty("round_number")
        private Integer roundNumber;

        @JsonProperty("story_title")
        private String storyTitle;

        @JsonProperty("votes")
        private List<VoteDetailDTO> votes;

        @JsonProperty("average")
        private BigDecimal average;

        @JsonProperty("median")
        private String median;

        @JsonProperty("consensus_reached")
        private Boolean consensusReached;
    }

    public static class VoteDetailDTO {
        @JsonProperty("participant_name")
        private String participantName;

        @JsonProperty("card_value")
        private String cardValue;

        @JsonProperty("voted_at")
        private Instant votedAt;
    }
}
```

This structure provides a complete guide for implementing the ReportingService with tier-based access control, detailed analytics, and export job enqueuing capabilities.
