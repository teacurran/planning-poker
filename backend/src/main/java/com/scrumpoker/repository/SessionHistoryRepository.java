package com.scrumpoker.repository;

import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.SessionHistoryId;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for SessionHistory entity.
 * Uses composite primary key (sessionId + startedAt) for partition support.
 * SessionHistory is partitioned by month for performance optimization.
 */
@ApplicationScoped
public class SessionHistoryRepository implements PanacheRepositoryBase<SessionHistory, SessionHistoryId> {

    /**
     * Find all session history records for a room, ordered by start time.
     *
     * @param roomId The room ID (6-character string)
     * @return Uni of list of session history records
     */
    public Uni<List<SessionHistory>> findByRoomId(String roomId) {
        return find("room.roomId = ?1 order by id.startedAt desc", roomId).list();
    }

    /**
     * Find session history within a date range.
     * Optimized for partition pruning when querying time-based partitions.
     *
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni of list of sessions within the date range
     */
    public Uni<List<SessionHistory>> findByDateRange(Instant startDate, Instant endDate) {
        return find("id.startedAt >= ?1 and id.startedAt <= ?2 order by id.startedAt desc",
                    startDate, endDate).list();
    }

    /**
     * Find session history by session ID.
     *
     * @param sessionId The session ID
     * @return Uni of list of session history records with the given session ID
     */
    public Uni<List<SessionHistory>> findBySessionId(UUID sessionId) {
        return find("id.sessionId", sessionId).list();
    }

    /**
     * Find recent sessions for a room (last N days).
     *
     * @param roomId The room ID
     * @param since The timestamp threshold
     * @return Uni of list of recent sessions
     */
    public Uni<List<SessionHistory>> findRecentByRoomId(String roomId, Instant since) {
        return find("room.roomId = ?1 and id.startedAt >= ?2 order by id.startedAt desc",
                    roomId, since).list();
    }

    /**
     * Count total sessions for a room.
     *
     * @param roomId The room ID
     * @return Uni containing the session count
     */
    public Uni<Long> countByRoomId(String roomId) {
        return count("room.roomId", roomId);
    }

    /**
     * Find sessions with high round counts.
     * Useful for analytics on long sessions.
     *
     * @param minRounds Minimum number of rounds
     * @return Uni of list of sessions with at least the specified round count
     */
    public Uni<List<SessionHistory>> findByMinRounds(Integer minRounds) {
        return find("totalRounds >= ?1 order by id.startedAt desc", minRounds).list();
    }
}
