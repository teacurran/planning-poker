package com.scrumpoker.domain.room;

import com.scrumpoker.event.RoomEventPublisher;
import com.scrumpoker.repository.RoomParticipantRepository;
import com.scrumpoker.repository.RoomRepository;
import com.scrumpoker.repository.RoundRepository;
import com.scrumpoker.repository.VoteRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    RoomEventPublisher roomEventPublisher;

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
     * Publishes a "round.revealed.v1" event with all votes and statistics.
     * </p>
     *
     * @param roomId The room ID (6-character nanoid)
     * @param roundId The round ID (UUID)
     * @return Uni containing the updated Round entity
     */
    @WithTransaction
    public Uni<Round> revealRound(String roomId, UUID roundId) {
        // Fetch all votes for the round
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
                    .onItem().call(updatedRound -> publishRoundRevealedEvent(roomId, updatedRound, votes));
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
}
