package com.scrumpoker.repository;

import com.scrumpoker.domain.room.Vote;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for Vote entity.
 * Provides custom finder methods for round-based and participant-based vote queries.
 */
@ApplicationScoped
public class VoteRepository implements PanacheRepositoryBase<Vote, UUID> {

    /**
     * Find all votes for a specific round, ordered by vote time.
     * This is a critical query used during vote reveal.
     *
     * @param roundId The round ID
     * @return Uni of list of votes in the round
     */
    public Uni<List<Vote>> findByRoundId(UUID roundId) {
        return find("round.roundId = ?1 order by votedAt", roundId).list();
    }

    /**
     * Find votes by room ID and round number.
     * Alternative query pattern for round-based vote retrieval.
     *
     * @param roomId The room ID (6-character string)
     * @param roundNumber The round number within the session
     * @return Uni of list of votes for the specified round
     */
    public Uni<List<Vote>> findByRoomIdAndRoundNumber(String roomId, Integer roundNumber) {
        return find("round.room.roomId = ?1 and round.roundNumber = ?2 order by votedAt",
                    roomId, roundNumber).list();
    }

    /**
     * Find all votes by a specific participant.
     *
     * @param participantId The participant ID
     * @return Uni of list of votes cast by the participant
     */
    public Uni<List<Vote>> findByParticipantId(UUID participantId) {
        return find("participant.participantId = ?1 order by votedAt", participantId).list();
    }

    /**
     * Find a specific participant's vote in a round.
     *
     * @param roundId The round ID
     * @param participantId The participant ID
     * @return Uni containing the vote if found, or null if not found
     */
    public Uni<Vote> findByRoundIdAndParticipantId(UUID roundId, UUID participantId) {
        return find("round.roundId = ?1 and participant.participantId = ?2",
                    roundId, participantId).firstResult();
    }

    /**
     * Count total votes in a round.
     * Used to determine if all voters have cast their votes.
     *
     * @param roundId The round ID
     * @return Uni containing the vote count
     */
    public Uni<Long> countByRoundId(UUID roundId) {
        return count("round.roundId", roundId);
    }

    /**
     * Find votes with a specific card value in a round.
     * Useful for consensus detection.
     *
     * @param roundId The round ID
     * @param cardValue The card value to search for
     * @return Uni of list of votes with the specified card value
     */
    public Uni<List<Vote>> findByRoundIdAndCardValue(UUID roundId, String cardValue) {
        return find("round.roundId = ?1 and cardValue = ?2", roundId, cardValue).list();
    }
}
