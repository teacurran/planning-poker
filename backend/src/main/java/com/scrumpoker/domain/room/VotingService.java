package com.scrumpoker.domain.room;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.reporting.ParticipantSummary;
import com.scrumpoker.domain.reporting.SessionSummaryStats;
import com.scrumpoker.event.RoomEventPublisher;
import com.scrumpoker.repository.RoomParticipantRepository;
import com.scrumpoker.repository.RoomRepository;
import com.scrumpoker.repository.RoundRepository;
import com.scrumpoker.repository.SessionHistoryRepository;
import com.scrumpoker.repository.VoteRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Domain service for voting operations in estimation rounds.
 * Implements vote casting, round lifecycle management, and statistics calculation.
 * All methods use reactive Mutiny patterns for non-blocking I/O.
 */
@ApplicationScoped
public class VotingService {

    @Inject
    RoundRepository roundRepository;

    @Inject
    VoteRepository voteRepository;

    @Inject
    RoomRepository roomRepository;

    @Inject
    RoomParticipantRepository roomParticipantRepository;

    @Inject
    SessionHistoryRepository sessionHistoryRepository;

    @Inject
    RoomEventPublisher roomEventPublisher;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Casts a vote for a participant in an estimation round.
     * Implements upsert logic: updates existing vote if participant has already voted,
     * otherwise creates a new vote.
     * <p>
     * Publishes a "vote.recorded.v1" event after successful persistence.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param roundId The round ID (UUID)
     * @param participantId The participant ID (UUID)
     * @param cardValue The card value (e.g., "5", "?", "∞", "☕")
     * @return Uni containing the persisted Vote entity
     */
    @WithTransaction
    public Uni<Vote> castVote(String roomId, UUID roundId, UUID participantId, String cardValue) {
        // Validate input
        if (cardValue == null || cardValue.trim().isEmpty()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Card value cannot be null or empty"));
        }
        if (cardValue.length() > 10) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Card value cannot exceed 10 characters"));
        }

        // Check if participant has already voted (for upsert)
        return voteRepository.findByRoundIdAndParticipantId(roundId, participantId)
                .onItem().transformToUni(existingVote -> {
                    if (existingVote != null) {
                        // Update existing vote
                        existingVote.cardValue = cardValue.trim();
                        existingVote.votedAt = Instant.now();
                        return voteRepository.persist(existingVote);
                    } else {
                        // Create new vote - need to fetch Round and RoomParticipant entities first
                        return createNewVote(roundId, participantId, cardValue.trim());
                    }
                })
                .onItem().call(vote -> publishVoteRecordedEvent(roomId, vote));
    }

    /**
     * Starts a new estimation round in a room.
     * Creates a Round entity with the next round number, story title, and started timestamp.
     * <p>
     * Publishes a "round.started.v1" event after successful persistence.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param storyTitle The story title for the round (max 500 characters)
     * @return Uni containing the created Round entity
     */
    @WithTransaction
    public Uni<Round> startRound(String roomId, String storyTitle) {
        // Validate input
        if (storyTitle != null && storyTitle.length() > 500) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Story title cannot exceed 500 characters"));
        }

        // Fetch Room entity and latest round to determine next round number
        return Uni.combine().all().unis(
                roomRepository.findById(roomId),
                roundRepository.findLatestByRoomId(roomId)
        ).asTuple()
        .onItem().transformToUni(tuple -> {
            Room room = tuple.getItem1();
            Round latestRound = tuple.getItem2();

            if (room == null) {
                return Uni.createFrom().failure(
                        new IllegalArgumentException("Room not found: " + roomId));
            }

            // Determine next round number
            Integer nextRoundNumber = (latestRound != null) ? latestRound.roundNumber + 1 : 1;

            // Create new Round entity
            Round round = new Round();
            round.room = room;
            round.roundNumber = nextRoundNumber;
            round.storyTitle = (storyTitle != null) ? storyTitle.trim() : null;
            round.startedAt = Instant.now();
            round.revealedAt = null;
            round.average = null;
            round.median = null;
            round.consensusReached = false;

            return roundRepository.persist(round);
        })
        .onItem().call(round -> publishRoundStartedEvent(roomId, round));
    }

    /**
     * Reveals an estimation round by calculating statistics and updating the Round entity.
     * <p>
     * Calculates:
     * - Average: mean of numeric votes (excluding non-numeric cards)
     * - Median: middle value for numeric votes, or most common value for mixed votes
     * - Consensus: true if variance < 2.0 for all numeric votes
     * </p>
     * <p>
     * After revealing the round, updates the SessionHistory record with:
     * - Round count increment
     * - Participant summaries (vote counts per participant)
     * - Summary statistics (total votes, consensus rate)
     * </p>
     * <p>
     * Publishes a "round.revealed.v1" event with all votes and statistics.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param roundId The round ID (UUID)
     * @return Uni containing the updated Round entity
     */
    @WithTransaction
    public Uni<Round> revealRound(String roomId, UUID roundId) {
        // Fetch all votes for the round and the round entity
        return Uni.combine().all().unis(
                voteRepository.findByRoundId(roundId),
                roundRepository.findById(roundId)
        ).asTuple()
        .onItem().transformToUni(tuple -> {
            List<Vote> votes = tuple.getItem1();
            Round round = tuple.getItem2();

            if (round == null) {
                return Uni.createFrom().failure(
                        new IllegalArgumentException("Round not found: " + roundId));
            }

            // Calculate statistics using ConsensusCalculator
            BigDecimal average = ConsensusCalculator.calculateAverage(votes);
            String median = ConsensusCalculator.calculateMedian(votes);
            boolean consensus = ConsensusCalculator.calculateConsensus(votes);

            // Update Round entity with statistics
            round.revealedAt = Instant.now();
            round.average = average;
            round.median = median;
            round.consensusReached = consensus;

            return roundRepository.persist(round)
                    .onItem().call(updatedRound -> publishRoundRevealedEvent(roomId, updatedRound, votes))
                    .onItem().call(updatedRound -> updateSessionHistory(roomId, updatedRound, votes));
        });
    }

    /**
     * Resets an estimation round by deleting all votes and clearing statistics.
     * The Round entity is preserved for audit trail but its statistics are reset.
     * <p>
     * Publishes a "round.reset.v1" event after successful reset.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param roundId The round ID (UUID)
     * @return Uni containing the reset Round entity
     */
    @WithTransaction
    public Uni<Round> resetRound(String roomId, UUID roundId) {
        // Delete all votes and fetch round in parallel
        return Uni.combine().all().unis(
                voteRepository.delete("round.roundId", roundId),
                roundRepository.findById(roundId)
        ).asTuple()
        .onItem().transformToUni(tuple -> {
            Round round = tuple.getItem2();

            if (round == null) {
                return Uni.createFrom().failure(
                        new IllegalArgumentException("Round not found: " + roundId));
            }

            // Reset statistics fields
            round.revealedAt = null;
            round.average = null;
            round.median = null;
            round.consensusReached = false;

            return roundRepository.persist(round);
        })
        .onItem().call(round -> publishRoundResetEvent(roomId, round));
    }

    /**
     * Creates a new Vote entity by fetching required entity relationships.
     *
     * @param roundId The round ID
     * @param participantId The participant ID
     * @param cardValue The card value
     * @return Uni containing the created Vote entity
     */
    private Uni<Vote> createNewVote(UUID roundId, UUID participantId, String cardValue) {
        return Uni.combine().all().unis(
                roundRepository.findById(roundId),
                roomParticipantRepository.findById(participantId)
        ).asTuple()
        .onItem().transformToUni(tuple -> {
            Round round = tuple.getItem1();
            RoomParticipant participant = tuple.getItem2();

            if (round == null) {
                return Uni.createFrom().failure(
                        new IllegalArgumentException("Round not found: " + roundId));
            }
            if (participant == null) {
                return Uni.createFrom().failure(
                        new IllegalArgumentException("Participant not found: " + participantId));
            }

            // Create new Vote entity
            Vote vote = new Vote();
            vote.round = round;
            vote.participant = participant;
            vote.cardValue = cardValue;
            vote.votedAt = Instant.now();

            return voteRepository.persist(vote);
        });
    }

    /**
     * Publishes a "vote.recorded.v1" event to Redis Pub/Sub.
     *
     * @param roomId The room ID
     * @param vote The persisted Vote entity
     * @return Uni<Void> that completes when event is published
     */
    private Uni<Void> publishVoteRecordedEvent(String roomId, Vote vote) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("participantId", vote.participant.participantId.toString());
        payload.put("votedAt", vote.votedAt.toString());

        return roomEventPublisher.publishEvent(roomId, "vote.recorded.v1", payload);
    }

    /**
     * Publishes a "round.started.v1" event to Redis Pub/Sub.
     *
     * @param roomId The room ID
     * @param round The created Round entity
     * @return Uni<Void> that completes when event is published
     */
    private Uni<Void> publishRoundStartedEvent(String roomId, Round round) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roundId", round.roundId.toString());
        payload.put("roundNumber", round.roundNumber);
        payload.put("storyTitle", round.storyTitle);
        payload.put("startedAt", round.startedAt.toString());

        return roomEventPublisher.publishEvent(roomId, "round.started.v1", payload);
    }

    /**
     * Publishes a "round.revealed.v1" event to Redis Pub/Sub with all votes and statistics.
     *
     * @param roomId The room ID
     * @param round The revealed Round entity
     * @param votes List of all votes in the round
     * @return Uni<Void> that completes when event is published
     */
    private Uni<Void> publishRoundRevealedEvent(String roomId, Round round, List<Vote> votes) {
        // Build votes array for payload
        List<Map<String, Object>> votesPayload = votes.stream()
                .map(vote -> {
                    Map<String, Object> voteMap = new HashMap<>();
                    voteMap.put("participantId", vote.participant.participantId.toString());
                    voteMap.put("cardValue", vote.cardValue);
                    return voteMap;
                })
                .toList();

        // Build stats object
        Map<String, Object> stats = new HashMap<>();
        stats.put("avg", round.average != null ? round.average.doubleValue() : null);
        stats.put("median", round.median);
        stats.put("consensus", round.consensusReached);

        // Build full payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("votes", votesPayload);
        payload.put("stats", stats);
        payload.put("revealedAt", round.revealedAt.toString());

        return roomEventPublisher.publishEvent(roomId, "round.revealed.v1", payload);
    }

    /**
     * Publishes a "round.reset.v1" event to Redis Pub/Sub.
     *
     * @param roomId The room ID
     * @param round The reset Round entity
     * @return Uni<Void> that completes when event is published
     */
    private Uni<Void> publishRoundResetEvent(String roomId, Round round) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("roundId", round.roundId.toString());

        return roomEventPublisher.publishEvent(roomId, "round.reset.v1", payload);
    }

    /**
     * Updates or creates SessionHistory record after revealing a round.
     * <p>
     * Session tracking strategy:
     * - Creates a new SessionHistory record if this is the first revealed round in the room
     * - Updates existing SessionHistory record for subsequent rounds in the same session
     * - A "session" is defined as continuous estimation activity in a room
     * </p>
     * <p>
     * Updates performed:
     * - Increment totalRounds counter
     * - Update participants JSONB array with vote counts per participant
     * - Recalculate summary statistics (consensus rate, total votes)
     * - Update endedAt timestamp to current time
     * </p>
     *
     * @param roomId The room ID
     * @param revealedRound The revealed round with statistics
     * @param votes List of votes in the revealed round
     * @return Uni<Void> that completes when SessionHistory is updated
     */
    private Uni<Void> updateSessionHistory(String roomId, Round revealedRound, List<Vote> votes) {
        // Fetch all rounds for the room to determine session boundaries and calculate aggregate stats
        return roundRepository.findRevealedByRoomId(roomId)
                .onItem().transformToUni(allRevealedRounds -> {
                    if (allRevealedRounds.isEmpty()) {
                        Log.warn("No revealed rounds found for room " + roomId + " despite revealing a round");
                        return Uni.createFrom().voidItem();
                    }

                    // Determine session start time (first revealed round's start time)
                    Instant sessionStartedAt = allRevealedRounds.stream()
                            .map(r -> r.startedAt)
                            .min(Instant::compareTo)
                            .orElse(revealedRound.startedAt);

                    // Try to find existing session history for this room starting at this time
                    // Use repository method with native SQL to avoid Hibernate Reactive @EmbeddedId bug
                    return sessionHistoryRepository.findByRoomIdAndStartedAt(
                            roomId, sessionStartedAt)
                    .onItem().transformToUni(existingSession -> {
                        if (existingSession != null) {
                            // Update existing session
                            return updateExistingSessionHistory(
                                    existingSession, allRevealedRounds, roomId);
                        } else {
                            // Create new session history record
                            return createNewSessionHistory(
                                    roomId, sessionStartedAt, allRevealedRounds);
                        }
                    });
                })
                .replaceWithVoid()
                .onFailure().invoke(throwable ->
                        Log.error("Failed to update session history for room " + roomId, throwable));
    }

    /**
     * Creates a new SessionHistory record for the first revealed round in a room.
     *
     * @param roomId The room ID
     * @param sessionStartedAt The session start timestamp
     * @param allRevealedRounds All revealed rounds in the room
     * @return Uni<SessionHistory> containing the created record
     */
    private Uni<SessionHistory> createNewSessionHistory(
            String roomId, Instant sessionStartedAt, List<Round> allRevealedRounds) {

        return roomRepository.findById(roomId)
                .onItem().transformToUni(room -> {
                    if (room == null) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException("Room not found: " + roomId));
                    }

                    // Generate new session ID
                    UUID sessionId = UUID.randomUUID();

                    // Fetch all votes for all revealed rounds to calculate participant summaries
                    return fetchAllVotesForRounds(allRevealedRounds)
                            .onItem().transformToUni(allVotes -> {
                                try {
                                    // Build participant summaries
                                    List<ParticipantSummary> participantSummaries =
                                            buildParticipantSummaries(allVotes);
                                    String participantsJson = objectMapper.writeValueAsString(participantSummaries);

                                    // Build summary statistics
                                    SessionSummaryStats stats = buildSummaryStats(allRevealedRounds, allVotes);
                                    String summaryStatsJson = objectMapper.writeValueAsString(stats);

                                    // Create SessionHistory entity
                                    SessionHistory sessionHistory = new SessionHistory();
                                    sessionHistory.id = new SessionHistoryId(sessionId, sessionStartedAt);
                                    sessionHistory.room = room;
                                    sessionHistory.endedAt = Instant.now();
                                    sessionHistory.totalRounds = allRevealedRounds.size();
                                    sessionHistory.totalStories = allRevealedRounds.size(); // One story per round
                                    sessionHistory.participants = participantsJson;
                                    sessionHistory.summaryStats = summaryStatsJson;

                                    return sessionHistoryRepository.persist(sessionHistory);

                                } catch (JsonProcessingException e) {
                                    Log.error("Failed to serialize session history JSON", e);
                                    return Uni.createFrom().failure(e);
                                }
                            });
                });
    }

    /**
     * Updates an existing SessionHistory record with new round data.
     *
     * @param existingSession The existing SessionHistory record
     * @param allRevealedRounds All revealed rounds in the room
     * @param roomId The room ID
     * @return Uni<SessionHistory> containing the updated record
     */
    private Uni<SessionHistory> updateExistingSessionHistory(
            SessionHistory existingSession, List<Round> allRevealedRounds, String roomId) {

        // Fetch all votes for all revealed rounds
        return fetchAllVotesForRounds(allRevealedRounds)
                .onItem().transformToUni(allVotes -> {
                    try {
                        // Rebuild participant summaries with updated vote counts
                        List<ParticipantSummary> participantSummaries =
                                buildParticipantSummaries(allVotes);
                        String participantsJson = objectMapper.writeValueAsString(participantSummaries);

                        // Recalculate summary statistics
                        SessionSummaryStats stats = buildSummaryStats(allRevealedRounds, allVotes);
                        String summaryStatsJson = objectMapper.writeValueAsString(stats);

                        // Update existing session history
                        existingSession.endedAt = Instant.now();
                        existingSession.totalRounds = allRevealedRounds.size();
                        existingSession.totalStories = allRevealedRounds.size();
                        existingSession.participants = participantsJson;
                        existingSession.summaryStats = summaryStatsJson;

                        return sessionHistoryRepository.persist(existingSession);

                    } catch (JsonProcessingException e) {
                        Log.error("Failed to serialize session history JSON for room " + roomId, e);
                        return Uni.createFrom().failure(e);
                    }
                });
    }

    /**
     * Fetches all votes for a list of rounds.
     *
     * @param rounds List of rounds
     * @return Uni containing a flat list of all votes
     */
    private Uni<List<Vote>> fetchAllVotesForRounds(List<Round> rounds) {
        if (rounds.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        // Fetch votes for each round in parallel
        List<Uni<List<Vote>>> voteUnis = rounds.stream()
                .map(round -> voteRepository.findByRoundId(round.roundId))
                .collect(Collectors.toList());

        return Uni.combine().all().unis(voteUnis)
                .with(voteLists -> {
                    // Flatten list of lists into a single list
                    return voteLists.stream()
                            .flatMap(voteList -> ((List<Vote>) voteList).stream())
                            .collect(Collectors.toList());
                });
    }

    /**
     * Builds participant summaries from all votes.
     * Aggregates vote counts per participant.
     *
     * @param allVotes All votes across all rounds
     * @return List of ParticipantSummary objects
     */
    private List<ParticipantSummary> buildParticipantSummaries(List<Vote> allVotes) {
        // Group votes by participant
        Map<UUID, List<Vote>> votesByParticipant = allVotes.stream()
                .collect(Collectors.groupingBy(vote -> vote.participant.participantId));

        return votesByParticipant.entrySet().stream()
                .map(entry -> {
                    RoomParticipant participant = entry.getValue().get(0).participant;
                    int voteCount = entry.getValue().size();
                    boolean isAuthenticated = participant.user != null;

                    return new ParticipantSummary(
                            participant.participantId,
                            participant.displayName,
                            participant.role.name(),
                            voteCount,
                            isAuthenticated
                    );
                })
                .sorted(Comparator.comparing(ParticipantSummary::getVoteCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Builds summary statistics from all revealed rounds and votes.
     *
     * @param allRevealedRounds All revealed rounds
     * @param allVotes All votes across all rounds
     * @return SessionSummaryStats object
     */
    private SessionSummaryStats buildSummaryStats(List<Round> allRevealedRounds, List<Vote> allVotes) {
        int totalVotes = allVotes.size();

        // Count rounds with consensus
        int roundsWithConsensus = (int) allRevealedRounds.stream()
                .filter(round -> round.consensusReached != null && round.consensusReached)
                .count();

        // Calculate consensus rate
        BigDecimal consensusRate = allRevealedRounds.isEmpty()
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(roundsWithConsensus)
                        .divide(BigDecimal.valueOf(allRevealedRounds.size()), 4, RoundingMode.HALF_UP);

        // Calculate average estimation time (time from round start to reveal)
        long totalEstimationTimeSeconds = allRevealedRounds.stream()
                .filter(round -> round.revealedAt != null)
                .mapToLong(round -> round.revealedAt.getEpochSecond() - round.startedAt.getEpochSecond())
                .sum();

        Long avgEstimationTimeSeconds = allRevealedRounds.isEmpty()
                ? 0L
                : totalEstimationTimeSeconds / allRevealedRounds.size();

        return new SessionSummaryStats(
                totalVotes,
                consensusRate,
                avgEstimationTimeSeconds,
                roundsWithConsensus
        );
    }
}
