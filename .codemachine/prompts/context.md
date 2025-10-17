# Task Briefing Package

This package contains all necessary information and strategic guidance for the Coder Agent.

---

## 1. Current Task Details

This is the full specification of the task you must complete.

```json
{
  "task_id": "I4.T3",
  "iteration_id": "I4",
  "iteration_goal": "Implement WebSocket-based real-time voting functionality including connection management, vote casting, round lifecycle (start, reveal, reset), Redis Pub/Sub for event broadcasting across stateless nodes, and frontend voting UI.",
  "description": "Create `VotingService` domain service implementing voting logic. Methods: `castVote(roomId, roundId, participantId, cardValue)` (persist vote to database, publish `vote.recorded` event), `startRound(roomId, storyTitle)` (create Round entity, publish `round.started` event), `revealRound(roomId, roundId)` (query all votes, calculate average/median/consensus, update Round entity with stats, publish `round.revealed` event with all votes), `resetRound(roomId, roundId)` (delete votes, reset Round entity). Use `RoundRepository`, `VoteRepository`, `RoomEventPublisher`. Implement consensus algorithm (variance threshold < 2 points for Fibonacci deck). Handle duplicate vote prevention (upsert vote if participant votes twice).",
  "agent_type_hint": "BackendAgent",
  "inputs": "Voting requirements from product spec, Vote sequence diagram from architecture blueprint, Round and Vote entities from I1",
  "input_files": [
    ".codemachine/artifacts/architecture/04_Behavior_and_Communication.md",
    "backend/src/main/java/com/scrumpoker/domain/room/Round.java",
    "backend/src/main/java/com/scrumpoker/domain/room/Vote.java",
    "backend/src/main/java/com/scrumpoker/repository/RoundRepository.java",
    "backend/src/main/java/com/scrumpoker/repository/VoteRepository.java"
  ],
  "target_files": [
    "backend/src/main/java/com/scrumpoker/domain/room/VotingService.java",
    "backend/src/main/java/com/scrumpoker/domain/room/ConsensusCalculator.java"
  ],
  "deliverables": "VotingService with methods: castVote, startRound, revealRound, resetRound, Vote persistence with duplicate handling (upsert by participant + round), Round creation with story title, started timestamp, Reveal logic: query votes, calculate stats (avg, median, consensus), persist, ConsensusCalculator determining consensus based on variance threshold, Event publishing after each operation (vote recorded, round started, revealed, reset)",
  "acceptance_criteria": "Cast vote persists to database and publishes event, Starting round creates Round entity with correct timestamp, Reveal round calculates correct average and median (test with known vote values), Consensus detection works (e.g., all votes 5 → consensus true, votes 3,5,8 → false), Duplicate vote from same participant updates existing vote (not create new), Reset round deletes votes and resets Round entity",
  "dependencies": ["I2.T3", "I4.T2"],
  "parallelizable": false,
  "done": false
}
```

---

## 2. Architectural & Planning Context

The following are the relevant sections from the architecture and plan documents, which I found by analyzing the task description.

### Context: Vote Casting and Round Reveal Sequence Diagram (from 04_Behavior_and_Communication.md)

```markdown
#### Key Interaction Flow: Vote Casting and Round Reveal

##### Description

This sequence diagram illustrates the critical real-time workflow for a Scrum Poker estimation round, from initial vote casting through final reveal and consensus calculation. The flow demonstrates WebSocket message handling, Redis Pub/Sub event distribution across stateless application nodes, and optimistic UI updates with server reconciliation.

**Scenario:**
1. Two participants (Alice and Bob) connected to different application nodes due to load balancer sticky session routing
2. Alice casts vote "5", Bob casts vote "8"
3. Host triggers reveal after all votes submitted
4. System calculates statistics (average: 6.5, median: 6.5, no consensus due to variance)
5. All participants receive synchronized reveal event with results

**Key Operations from Sequence:**

**Vote Casting:**
```
WS_A -> VS_A : castVote(roomId="abc123", participantId="alice", cardValue="5")
VS_A -> DB : INSERT INTO vote (round_id, participant_id, card_value, voted_at) VALUES (...)
DB --> VS_A : Success
VS_A -> Redis : PUBLISH room:abc123 {"type":"vote.recorded.v1", "payload":{"participantId":"alice", "votedAt":"..."}}
```

**Round Reveal:**
```
WS_C -> VS_C : revealRound(roomId="abc123", roundId="...")
VS_C -> DB : SELECT card_value FROM vote WHERE round_id = ... AND participant_id IN (...)
DB --> VS_C : [{"participantId":"alice","cardValue":"5"},{"participantId":"bob","cardValue":"8"}]

VS_C -> VS_C : Calculate:
  - Average: (5+8)/2 = 6.5
  - Median: 6.5
  - Consensus: false (variance > threshold)

VS_C -> DB : UPDATE round SET revealed_at = NOW(), average = 6.5, median = 6.5, consensus_reached = false WHERE round_id = ...
DB --> VS_C : Success

VS_C -> Redis : PUBLISH room:abc123 {"type":"round.revealed.v1", "payload":{"votes":[...], "stats":{"avg":6.5,"median":6.5,"consensus":false}}}
```
```

### Context: Communication Patterns - WebSocket (from 04_Behavior_and_Communication.md)

```markdown
##### Asynchronous WebSocket (Event-Driven)

**Use Cases:**
- Real-time vote casting and vote state updates
- Room state synchronization (participant joins/leaves, host controls)
- Card reveal events with animated timing coordination
- Presence updates (typing indicators, ready states)
- Chat messages and emoji reactions

**Pattern Characteristics:**
- Persistent connection maintained for session duration
- Events broadcast via Redis Pub/Sub to all application nodes
- Client-side event handlers update local state optimistically, reconcile on server confirmation
- Heartbeat/ping-pong protocol for connection liveness detection
- Automatic reconnection with exponential backoff on connection loss

**Message Flow:**
1. Client sends WebSocket message: `{"type": "vote.cast.v1", "requestId": "uuid", "payload": {"cardValue": "5"}}`
2. Server validates, persists vote to PostgreSQL
3. Server publishes event to Redis channel: `room:{roomId}`
4. All application nodes subscribed to channel receive event
5. Each node broadcasts to locally connected clients in that room
6. Clients receive: `{"type": "vote.recorded.v1", "requestId": "uuid", "payload": {"participantId": "...", "votedAt": "..."}}`

**WebSocket Message Types:**
- `vote.cast.v1` - Participant submits vote
- `vote.recorded.v1` - Server confirms vote persisted (broadcast to room)
- `round.reveal.v1` - Host triggers card reveal
- `round.revealed.v1` - Server broadcasts reveal with statistics
- `round.reset.v1` - Host resets round for re-voting
- `round.started.v1` - Host starts new round (implied from other messages)
```

---

## 3. Codebase Analysis & Strategic Guidance

The following analysis is based on my direct review of the current codebase. Use these notes and tips to guide your implementation.

### Relevant Existing Code

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomService.java`
    *   **Summary:** This file contains the domain service for room management with reactive patterns using Quarkus Mutiny. It demonstrates the established pattern for domain services in this project: using `@ApplicationScoped`, `@WithTransaction` for transactional methods, `@WithSession` for read-only methods, and returning `Uni<>` for single results or `Multi<>` for lists.
    *   **Recommendation:** You MUST follow the exact same architectural pattern in `VotingService`. Use `@WithTransaction` for write operations (castVote, startRound, revealRound, resetRound) and return reactive `Uni<>` types. Inject repositories using `@Inject`.
    *   **Pattern Example:** The service uses `@Inject ObjectMapper` for JSON serialization/deserialization, which you may also need for building event payloads.

*   **File:** `backend/src/main/java/com/scrumpoker/event/RoomEventPublisher.java`
    *   **Summary:** This file implements the Redis Pub/Sub event publisher that broadcasts WebSocket events to all application nodes. It provides the `publishEvent(roomId, type, requestId, payload)` method that serializes events to JSON and publishes them to Redis channels named `room:{roomId}`.
    *   **Recommendation:** You MUST inject and use `RoomEventPublisher` in your `VotingService` to broadcast events after each operation. For example, after casting a vote, call `roomEventPublisher.publishEvent(roomId, "vote.recorded.v1", requestId, payload)` where payload is a `Map<String, Object>` containing participantId and votedAt fields.
    *   **Critical:** Event publishing should happen AFTER successful database persistence to ensure consistency. Use reactive chaining: `persist().onItem().call(entity -> publishEvent(...))` to ensure events only fire for successfully committed changes.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Round.java`
    *   **Summary:** This JPA entity defines the Round table structure with fields: `roundId` (UUID), `room` (ManyToOne relationship), `roundNumber` (Integer), `storyTitle` (String, max 500 chars), `startedAt` (Instant), `revealedAt` (Instant, nullable), `average` (BigDecimal for numeric average), `median` (String to support non-numeric cards like ?, ∞, ☕), and `consensusReached` (Boolean).
    *   **Recommendation:** When creating a Round in `startRound()`, you MUST set `startedAt = Instant.now()` and ensure `revealedAt` is null initially. In `revealRound()`, you MUST update `revealedAt`, `average`, `median`, and `consensusReached` fields. The median field is VARCHAR(10) to support special card values.
    *   **Note:** The Round has a unique constraint on `(room_id, round_number)`. You must fetch the Room entity and set `round.room = roomEntity` when creating a new round.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/Vote.java`
    *   **Summary:** This JPA entity defines the Vote table with fields: `voteId` (UUID), `round` (ManyToOne to Round), `participant` (ManyToOne to RoomParticipant), `cardValue` (String, max 10 chars), and `votedAt` (Instant). There's a unique constraint on `(round_id, participant_id)` preventing duplicate votes per participant per round.
    *   **Recommendation:** For duplicate vote handling in `castVote()`, you MUST use the repository's `findByRoundIdAndParticipantId()` method first. If a vote exists, UPDATE the existing vote's cardValue and votedAt timestamp. If not, create a new Vote entity. This is an UPSERT pattern. DO NOT try to insert twice - the unique constraint will fail.
    *   **Critical:** When creating a new Vote, you must fetch the Round entity and RoomParticipant entity to set the relationships: `vote.round = roundEntity` and `vote.participant = participantEntity`.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/RoundRepository.java`
    *   **Summary:** This Panache repository provides reactive query methods for Round entities, including `findByRoomId()`, `findByRoomIdAndRoundNumber()`, `findLatestByRoomId()`, and `countByRoomId()`. All methods return `Uni<>` or `Uni<List<>>` for reactive execution.
    *   **Recommendation:** You SHOULD use `findLatestByRoomId(roomId)` in `startRound()` to determine the next round number (latest.roundNumber + 1, or 1 if no rounds exist). You will also need to inject `RoomRepository` to fetch the Room entity when creating rounds.

*   **File:** `backend/src/main/java/com/scrumpoker/repository/VoteRepository.java`
    *   **Summary:** This Panache repository provides vote query methods including `findByRoundId()` (critical for reveal), `findByRoundIdAndParticipantId()` (for duplicate detection), and `countByRoundId()`. All methods are reactive.
    *   **Recommendation:** You MUST use `findByRoundId(roundId)` in `revealRound()` to retrieve all votes for statistics calculation. Use `findByRoundIdAndParticipantId(roundId, participantId)` in `castVote()` for upsert logic. For `resetRound()`, you SHOULD use `delete("round.roundId", roundId)` to bulk delete all votes for the round.

*   **File:** `backend/src/main/java/com/scrumpoker/domain/room/RoomParticipant.java`
    *   **Summary:** This entity represents participants in a room with fields: `participantId` (UUID), `room`, `user` (nullable for anonymous), `anonymousId` (nullable, for anonymous users), `displayName`, `role` (enum: HOST, VOTER, OBSERVER), `connectedAt`, `disconnectedAt`. The Vote entity has a foreign key to this participant_id.
    *   **Recommendation:** Your `castVote()` method signature should accept a `participantId` (UUID) parameter, which will be the `RoomParticipant.participantId`, not the user ID directly. You will need to inject `RoomParticipantRepository` to fetch the RoomParticipant entity when creating votes.

### Implementation Tips & Notes

*   **Tip - Consensus Algorithm:** The specification says "variance threshold < 2 points for Fibonacci deck". Here's the algorithm:
    1. Filter votes to only numeric values (1, 2, 3, 5, 8, 13)
    2. If any votes are non-numeric (?, ∞, ☕), consensus is automatically FALSE
    3. If all votes are the same value, consensus is TRUE (variance = 0)
    4. Calculate variance: σ² = Σ(xi - μ)² / n where μ is mean
    5. If variance < 2.0, consensus is TRUE; otherwise FALSE

    Create a separate `ConsensusCalculator` utility class with a static method like `public static boolean calculateConsensus(List<Vote> votes)` that returns a boolean. Include a constant `VARIANCE_THRESHOLD = 2.0`.

*   **Tip - Median Calculation:** For median:
    - If all votes are numeric: sort values, take middle value (or average of two middle values if even count)
    - If any votes are non-numeric: set median to the most common vote value, or "mixed" if no clear majority
    - The median field in Round is VARCHAR(10) to support both numeric ("5") and non-numeric ("?") values

*   **Tip - Average Calculation:** For average:
    - Only include numeric votes in calculation
    - Filter out non-numeric card values (?, ∞, ☕)
    - Use `BigDecimal.valueOf(sum / count)` and set scale to 2 with `RoundingMode.HALF_UP`
    - If no numeric votes exist, set average to NULL

*   **Note - Event Payload Structure:** The event payloads MUST match the WebSocket protocol specification:
    - `vote.recorded.v1` payload: `{"participantId": "uuid-string", "votedAt": "2025-10-17T12:34:56Z"}`
    - `round.started.v1` payload: `{"roundId": "uuid-string", "roundNumber": 1, "storyTitle": "User story", "startedAt": "..."}`
    - `round.revealed.v1` payload: `{"votes": [{"participantId": "...", "cardValue": "5"}, ...], "stats": {"avg": 6.5, "median": "6.5", "consensus": false}, "revealedAt": "..."}`
    - `round.reset.v1` payload: `{"roundId": "uuid-string"}`

*   **Note - Reactive Programming Pattern:** All service methods should return `Uni<>` types. Chain operations using `.onItem().transformToUni()` or `.flatMap()` for sequential async operations. Example pattern for castVote:
    ```java
    return voteRepository.findByRoundIdAndParticipantId(roundId, participantId)
        .onItem().transformToUni(existingVote -> {
            if (existingVote != null) {
                // Update existing
                existingVote.cardValue = cardValue;
                existingVote.votedAt = Instant.now();
                return voteRepository.persist(existingVote);
            } else {
                // Create new - need to fetch Round and RoomParticipant first
                return fetchEntitiesAndCreateVote(roundId, participantId, cardValue);
            }
        })
        .onItem().call(vote -> publishVoteRecordedEvent(roomId, vote));
    ```

*   **Warning - Transaction Boundaries:** Each method that modifies data (castVote, startRound, revealRound, resetRound) MUST be annotated with `@WithTransaction` to ensure atomicity. Event publishing should happen INSIDE the transaction scope but AFTER the database operation succeeds using `.onItem().call()` to ensure we only publish events for successfully persisted changes.

*   **Warning - Entity Relationships:** When creating a Round or Vote, you MUST fetch and set the entity relationships correctly:
    - For Round: fetch Room entity via `RoomRepository.findById(roomId)` and set `round.room = roomEntity`
    - For Vote: fetch Round entity via `RoundRepository.findById(roundId)` and RoomParticipant via `RoomParticipantRepository.findById(participantId)`, then set `vote.round = roundEntity` and `vote.participant = participantEntity`
    - Do NOT try to set relationships using just IDs - Hibernate requires actual entity references

*   **Critical - Reset Round Logic:** For `resetRound()`, you should NOT delete the Round entity itself - only delete the votes and reset the Round's statistics fields (set `revealedAt = null`, `average = null`, `median = null`, `consensusReached = false`). This maintains the audit trail of rounds while allowing re-voting. The pattern:
    ```java
    @WithTransaction
    public Uni<Round> resetRound(String roomId, UUID roundId) {
        return Uni.combine().all().unis(
            voteRepository.delete("round.roundId", roundId),  // Delete all votes
            roundRepository.findById(roundId)                  // Fetch round
        ).asTuple()
        .onItem().transformToUni(tuple -> {
            Round round = tuple.getItem2();
            round.revealedAt = null;
            round.average = null;
            round.median = null;
            round.consensusReached = false;
            return roundRepository.persist(round);
        })
        .onItem().call(round -> publishResetEvent(roomId, round));
    }
    ```

*   **Critical - Repository Injections:** You will need to inject FOUR repositories in VotingService:
    1. `@Inject RoundRepository roundRepository;`
    2. `@Inject VoteRepository voteRepository;`
    3. `@Inject RoomRepository roomRepository;` (to fetch Room entity when creating rounds)
    4. `@Inject RoomParticipantRepository roomParticipantRepository;` (to fetch RoomParticipant when creating votes)

    Plus:
    5. `@Inject RoomEventPublisher roomEventPublisher;` (for event broadcasting)

### Package Organization

*   Both `VotingService.java` and `ConsensusCalculator.java` belong in `backend/src/main/java/com/scrumpoker/domain/room/`
*   Follow existing naming conventions: service classes use noun names with "Service" suffix
*   Use `@ApplicationScoped` for VotingService to make it a CDI singleton
*   ConsensusCalculator should be a utility class with static methods (no CDI annotations needed)

### Method Signatures (Recommended)

```java
@ApplicationScoped
public class VotingService {

    @WithTransaction
    public Uni<Vote> castVote(String roomId, UUID roundId, UUID participantId, String cardValue) { ... }

    @WithTransaction
    public Uni<Round> startRound(String roomId, String storyTitle) { ... }

    @WithTransaction
    public Uni<Round> revealRound(String roomId, UUID roundId) { ... }

    @WithTransaction
    public Uni<Round> resetRound(String roomId, UUID roundId) { ... }
}

public class ConsensusCalculator {

    private static final double VARIANCE_THRESHOLD = 2.0;

    public static boolean calculateConsensus(List<Vote> votes) { ... }

    private static boolean isNumericCardValue(String cardValue) { ... }

    private static double calculateVariance(List<Double> numericValues) { ... }
}
```

---

## Summary Checklist for Coder Agent

Before you start coding, ensure you understand:

- [x] Service must follow reactive Mutiny patterns with `Uni<>` return types
- [x] Use `@WithTransaction` for all write operations
- [x] Inject 4 repositories: Round, Vote, Room, RoomParticipant
- [x] Inject RoomEventPublisher for broadcasting events
- [x] Implement upsert logic for duplicate votes using findByRoundIdAndParticipantId
- [x] Fetch entity references (Room, Round, RoomParticipant) before creating related entities
- [x] Calculate consensus using variance threshold < 2.0 for numeric votes only
- [x] Handle non-numeric card values (?, ∞, ☕) in median calculation
- [x] Event publishing happens AFTER successful persistence using `.onItem().call()`
- [x] Reset round deletes votes but preserves Round entity (sets fields to null)
- [x] Use `findLatestByRoomId()` to determine next round number in startRound
- [x] Event payloads must match WebSocket protocol specification format

**Next Steps:**
1. Create `ConsensusCalculator.java` utility class with variance calculation
2. Implement `VotingService.java` with all four methods
3. Ensure proper entity relationship handling for Vote and Round creation
4. Implement reactive event publishing after each operation
5. Test with known vote values to verify statistics calculations

Good luck! Remember to follow the reactive programming patterns and ensure proper transaction boundaries throughout.
