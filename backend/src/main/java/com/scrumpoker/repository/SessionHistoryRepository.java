package com.scrumpoker.repository;

import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.SessionHistoryId;
import io.quarkus.hibernate.reactive.panache.Panache;
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
 *
 * <p>
 * IMPORTANT: Due to a known bug in Hibernate Reactive with
 * EmbeddedId composite keys, all query methods use native SQL
 * instead of HQL/JPQL. HQL queries that reference fields within
 * EmbeddedId (e.g., "id.startedAt") fail with ClassCastException:
 * "EmbeddableInitializerImpl cannot be cast to ReactiveInitializer".
 * </p>
 *
 * <p>Bug reference:
 * https://github.com/hibernate/hibernate-reactive/issues/1791</p>
 */
@ApplicationScoped
public class SessionHistoryRepository
        implements PanacheRepositoryBase<SessionHistory,
                                          SessionHistoryId> {

    /**
     * Find all session history records for a room, ordered by start time.
     *
     * @param roomId The room ID (6-character string)
     * @return Uni of list of session history records
     */
    public Uni<List<SessionHistory>> findByRoomId(final String roomId) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT sh.* FROM session_history sh
                INNER JOIN room r ON sh.room_id = r.room_id
                WHERE r.room_id = ?1
                ORDER BY sh.started_at DESC
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, SessionHistory.class)
                        .setParameter(1, roomId)
                        .getResultList());
    }

    /**
     * Find session history within a date range.
     * Optimized for partition pruning when querying time-based partitions.
     *
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni of list of sessions within the date range
     */
    public Uni<List<SessionHistory>> findByDateRange(final Instant startDate,
                                                      final Instant endDate) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT sh.* FROM session_history sh
                WHERE sh.started_at >= ?1 AND sh.started_at <= ?2
                ORDER BY sh.started_at DESC
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, SessionHistory.class)
                        .setParameter(1, startDate)
                        .setParameter(2, endDate)
                        .getResultList());
    }

    /**
     * Find session history by session ID.
     *
     * @param sessionId The session ID
     * @return Uni of list of session history records with the given session ID
     */
    public Uni<List<SessionHistory>> findBySessionId(final UUID sessionId) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT sh.* FROM session_history sh
                WHERE sh.session_id = ?1
                ORDER BY sh.started_at DESC
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, SessionHistory.class)
                        .setParameter(1, sessionId)
                        .getResultList());
    }

    /**
     * Find recent sessions for a room (last N days).
     *
     * @param roomId The room ID
     * @param since The timestamp threshold
     * @return Uni of list of recent sessions
     */
    public Uni<List<SessionHistory>> findRecentByRoomId(final String roomId,
                                                         final Instant since) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT sh.* FROM session_history sh
                INNER JOIN room r ON sh.room_id = r.room_id
                WHERE r.room_id = ?1 AND sh.started_at >= ?2
                ORDER BY sh.started_at DESC
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, SessionHistory.class)
                        .setParameter(1, roomId)
                        .setParameter(2, since)
                        .getResultList());
    }

    /**
     * Count total sessions for a room.
     *
     * @param roomId The room ID
     * @return Uni containing the session count
     */
    public Uni<Long> countByRoomId(final String roomId) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT COUNT(*) FROM session_history sh
                INNER JOIN room r ON sh.room_id = r.room_id
                WHERE r.room_id = ?1
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, Long.class)
                        .setParameter(1, roomId)
                        .getSingleResult());
    }

    /**
     * Find sessions with high round counts.
     * Useful for analytics on long sessions.
     *
     * @param minRounds Minimum number of rounds
     * @return Uni of list of sessions with at least the specified round count
     */
    public Uni<List<SessionHistory>> findByMinRounds(final Integer minRounds) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT sh.* FROM session_history sh
                WHERE sh.total_rounds >= ?1
                ORDER BY sh.started_at DESC
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, SessionHistory.class)
                        .setParameter(1, minRounds)
                        .getResultList());
    }

    /**
     * Find session history by room ID and started_at.
     * This is the most efficient query for partitioned tables as it
     * uses both partition key criteria.
     *
     * @param roomId The room ID
     * @param startedAt The session start time
     * @return Uni containing the session, or null if not found
     */
    public Uni<SessionHistory> findByRoomIdAndStartedAt(
            final String roomId, final Instant startedAt) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT sh.* FROM session_history sh
                INNER JOIN room r ON sh.room_id = r.room_id
                WHERE r.room_id = ?1 AND sh.started_at = ?2
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, SessionHistory.class)
                        .setParameter(1, roomId)
                        .setParameter(2, startedAt)
                        .getSingleResultOrNull());
    }

    /**
     * Find session history by owner user ID and date range.
     * Optimized for partition pruning.
     *
     * @param ownerId The room owner's user ID
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni of list of sessions
     */
    public Uni<List<SessionHistory>> findByOwnerAndDateRange(
            final UUID ownerId, final Instant startDate,
            final Instant endDate) {
        // Use native SQL to avoid Hibernate Reactive @EmbeddedId bug
        final String sql = """
                SELECT sh.* FROM session_history sh
                INNER JOIN room r ON sh.room_id = r.room_id
                INNER JOIN "user" u ON r.owner_id = u.user_id
                WHERE u.user_id = ?1
                  AND sh.started_at >= ?2
                  AND sh.started_at <= ?3
                ORDER BY sh.started_at DESC
                """;
        return Panache.getSession()
                .chain(session -> session
                        .createNativeQuery(sql, SessionHistory.class)
                        .setParameter(1, ownerId)
                        .setParameter(2, startDate)
                        // SUPPRESS CHECKSTYLE MagicNumber
                        .setParameter(3, endDate)
                        .getResultList());
    }
}
