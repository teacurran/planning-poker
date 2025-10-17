package com.scrumpoker.repository;

import com.scrumpoker.domain.room.Round;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for Round entity.
 * Manages estimation rounds within rooms with round number-based queries.
 */
@ApplicationScoped
public class RoundRepository implements PanacheRepositoryBase<Round, UUID> {

    /**
     * Find all rounds for a room, ordered by round number.
     *
     * @param roomId The room ID (6-character string)
     * @return Uni of list of rounds in the room
     */
    public Uni<List<Round>> findByRoomId(String roomId) {
        return find("room.roomId = ?1 order by roundNumber", roomId).list();
    }

    /**
     * Find a specific round by room ID and round number.
     *
     * @param roomId The room ID
     * @param roundNumber The round number within the session
     * @return Uni containing the round if found, or null if not found
     */
    public Uni<Round> findByRoomIdAndRoundNumber(String roomId, Integer roundNumber) {
        return find("room.roomId = ?1 and roundNumber = ?2", roomId, roundNumber).firstResult();
    }

    /**
     * Find revealed rounds (completed rounds) for a room.
     *
     * @param roomId The room ID
     * @return Uni of list of revealed rounds
     */
    public Uni<List<Round>> findRevealedByRoomId(String roomId) {
        return find("room.roomId = ?1 and revealedAt is not null order by roundNumber", roomId).list();
    }

    /**
     * Find rounds where consensus was reached.
     *
     * @param roomId The room ID
     * @return Uni of list of rounds with consensus
     */
    public Uni<List<Round>> findConsensusRoundsByRoomId(String roomId) {
        return find("room.roomId = ?1 and consensusReached = true order by roundNumber", roomId).list();
    }

    /**
     * Find the latest round in a room.
     *
     * @param roomId The room ID
     * @return Uni containing the latest round, or null if no rounds exist
     */
    public Uni<Round> findLatestByRoomId(String roomId) {
        return find("room.roomId = ?1 order by roundNumber desc", roomId).firstResult();
    }

    /**
     * Count total rounds in a room.
     *
     * @param roomId The room ID
     * @return Uni containing the round count
     */
    public Uni<Long> countByRoomId(String roomId) {
        return count("room.roomId", roomId);
    }

    /**
     * Count rounds with consensus reached.
     *
     * @param roomId The room ID
     * @return Uni containing the count of consensus rounds
     */
    public Uni<Long> countConsensusRoundsByRoomId(String roomId) {
        return count("room.roomId = ?1 and consensusReached = true", roomId);
    }
}
