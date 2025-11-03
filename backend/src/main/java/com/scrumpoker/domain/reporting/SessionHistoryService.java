package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.SessionHistoryId;
import com.scrumpoker.repository.SessionHistoryRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Domain service for querying and analyzing session history data.
 * Provides tier-based reporting capabilities with
 * partition-optimized queries.
 * All methods use reactive Mutiny patterns for non-blocking I/O.
 */
@ApplicationScoped
public class SessionHistoryService {

    /**
     * Default rounding precision for BigDecimal calculations.
     */
    private static final int DECIMAL_SCALE = 4;

    /**
     * Maximum number of participants to include in statistics.
     */
    private static final int MAX_TOP_PARTICIPANTS = 5;

    /**
     * Repository for SessionHistory persistence operations.
     */
    @Inject
    private SessionHistoryRepository sessionHistoryRepository;

    /**
     * JSON object mapper for deserializing JSONB fields.
     */
    @Inject
    private ObjectMapper objectMapper;

    /**
     * Retrieves all sessions for a user within a date range.
     * Uses partition pruning for optimal query performance.
     * <p>
     * Query pattern: filters by owner user ID and date range
     * (partition key).
     * </p>
     *
     * @param userId The user ID (UUID)
     * @param from   Start date (inclusive)
     * @param to     End date (inclusive)
     * @return Uni containing list of SessionHistory records
     */
    public Uni<List<SessionHistory>> getUserSessions(
            final UUID userId, final Instant from, final Instant to) {
        if (userId == null || from == null || to == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "userId, from, and to cannot be null"));
        }

        if (from.isAfter(to)) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "'from' date must be before 'to' date"));
        }

        // Query with partition key (id.startedAt) for partition pruning
        return sessionHistoryRepository.find(
                "room.owner.userId = ?1 and id.startedAt >= ?2 "
                        + "and id.startedAt <= ?3 "
                        + "order by id.startedAt desc",
                userId, from, to
        ).list();
    }

    /**
     * Retrieves a single session by its session ID.
     * Note: This may scan multiple partitions if startedAt is not
     * provided.
     * For optimal performance, use
     * {@link #getSessionByIdAndDate(UUID, Instant)} if date is known.
     *
     * @param sessionId The session ID (UUID)
     * @return Uni containing the SessionHistory record, or null if
     *         not found
     */
    public Uni<SessionHistory> getSessionById(final UUID sessionId) {
        if (sessionId == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "sessionId cannot be null"));
        }

        // Query by session ID - may scan multiple partitions
        return sessionHistoryRepository.find("id.sessionId", sessionId)
                .firstResult();
    }

    /**
     * Retrieves a single session by its composite key (sessionId +
     * startedAt).
     * This is the most efficient query as it targets a specific
     * partition.
     *
     * @param sessionId The session ID (UUID)
     * @param startedAt The session start timestamp
     * @return Uni containing the SessionHistory record, or null if
     *         not found
     */
    public Uni<SessionHistory> getSessionByIdAndDate(
            final UUID sessionId, final Instant startedAt) {
        if (sessionId == null || startedAt == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "sessionId and startedAt cannot be null"));
        }

        final SessionHistoryId id = new SessionHistoryId(
                sessionId, startedAt);
        return sessionHistoryRepository.findById(id);
    }

    /**
     * Retrieves all sessions for a specific room.
     * Ordered by most recent first.
     *
     * @param roomId The room ID (6-character nanoid)
     * @return Uni containing list of SessionHistory records for the
     *         room
     */
    public Uni<List<SessionHistory>> getRoomSessions(
            final String roomId) {
        if (roomId == null || roomId.trim().isEmpty()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "roomId cannot be null or empty"));
        }

        return sessionHistoryRepository.findByRoomId(roomId);
    }

    /**
     * Retrieves sessions for a room within a date range
     * (partition-optimized).
     *
     * @param roomId The room ID (6-character nanoid)
     * @param from   Start date (inclusive)
     * @param to     End date (inclusive)
     * @return Uni containing list of SessionHistory records
     */
    public Uni<List<SessionHistory>> getRoomSessionsByDateRange(
            final String roomId, final Instant from, final Instant to) {
        if (roomId == null || from == null || to == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "roomId, from, and to cannot be null"));
        }

        if (from.isAfter(to)) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "'from' date must be before 'to' date"));
        }

        // Query with partition key for optimal performance
        return sessionHistoryRepository.find(
                "room.roomId = ?1 and id.startedAt >= ?2 "
                        + "and id.startedAt <= ?3 "
                        + "order by id.startedAt desc",
                roomId, from, to
        ).list();
    }

    /**
     * Calculates aggregate statistics for a user across all their
     * sessions.
     * <p>
     * Statistics include:
     * - Total sessions count
     * - Total rounds across all sessions
     * - Average consensus rate (weighted by rounds)
     * - Most active participants (by vote count)
     * </p>
     *
     * @param userId The user ID (UUID)
     * @param from   Start date (inclusive)
     * @param to     End date (inclusive)
     * @return Uni containing aggregate statistics map
     */
    public Uni<Map<String, Object>> getUserStatistics(
            final UUID userId, final Instant from, final Instant to) {
        return getUserSessions(userId, from, to)
                .onItem().transform(sessions -> {
                    if (sessions.isEmpty()) {
                        return Map.of(
                                "total_sessions", 0,
                                "total_rounds", 0,
                                "average_consensus_rate", BigDecimal.ZERO,
                                "most_active_participants", List.of()
                        );
                    }

                    // Calculate aggregate statistics
                    final int totalSessions = sessions.size();
                    final int totalRounds = sessions.stream()
                            .mapToInt(s -> s.totalRounds != null
                                    ? s.totalRounds : 0)
                            .sum();

                    // Calculate weighted average consensus rate
                    final BigDecimal totalConsensusRate = sessions.stream()
                            .map(session -> {
                                try {
                                    final SessionSummaryStats stats =
                                        objectMapper.readValue(
                                            session.summaryStats,
                                            SessionSummaryStats.class);
                                    return stats.getConsensusRate() != null
                                            ? stats.getConsensusRate()
                                            : BigDecimal.ZERO;
                                } catch (JsonProcessingException e) {
                                    return BigDecimal.ZERO;
                                }
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    final BigDecimal averageConsensusRate =
                            totalSessions > 0
                            ? totalConsensusRate.divide(
                                    BigDecimal.valueOf(totalSessions),
                                    DECIMAL_SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    // Aggregate participants across all sessions
                    final Map<String, Integer> participantVoteCounts =
                            sessions.stream()
                            .flatMap(session -> {
                                try {
                                    final List<ParticipantSummary> parts =
                                        objectMapper.readValue(
                                            session.participants,
                                            objectMapper.getTypeFactory()
                                                .constructCollectionType(
                                                    List.class,
                                                    ParticipantSummary.class)
                                        );
                                    return parts.stream();
                                } catch (JsonProcessingException e) {
                                    return List
                                        .<ParticipantSummary>of().stream();
                                }
                            })
                            .collect(Collectors.groupingBy(
                                    ParticipantSummary::getDisplayName,
                                    Collectors.summingInt(p ->
                                        p.getVoteCount() != null
                                            ? p.getVoteCount() : 0)
                            ));

                    // Find top most active participants
                    final List<Map<String, Object>> mostActive =
                            participantVoteCounts.entrySet().stream()
                            .sorted((e1, e2) ->
                                e2.getValue().compareTo(e1.getValue()))
                            .limit(MAX_TOP_PARTICIPANTS)
                            .map(entry -> Map.<String, Object>of(
                                    "display_name", entry.getKey(),
                                    "total_votes", entry.getValue()
                            ))
                            .collect(Collectors.toList());

                    return Map.of(
                            "total_sessions", totalSessions,
                            "total_rounds", totalRounds,
                            "average_consensus_rate", averageConsensusRate,
                            "most_active_participants", mostActive
                    );
                });
    }

    /**
     * Calculates aggregate statistics for a specific room.
     *
     * @param roomId The room ID (6-character nanoid)
     * @return Uni containing aggregate statistics map
     */
    public Uni<Map<String, Object>> getRoomStatistics(
            final String roomId) {
        return getRoomSessions(roomId)
                .onItem().transform(sessions -> {
                    if (sessions.isEmpty()) {
                        return Map.of(
                                "total_sessions", 0,
                                "total_rounds", 0,
                                "total_stories", 0,
                                "average_consensus_rate", BigDecimal.ZERO
                        );
                    }

                    final int totalSessions = sessions.size();
                    final int totalRounds = sessions.stream()
                            .mapToInt(s -> s.totalRounds != null
                                    ? s.totalRounds : 0)
                            .sum();
                    final int totalStories = sessions.stream()
                            .mapToInt(s -> s.totalStories != null
                                    ? s.totalStories : 0)
                            .sum();

                    // Calculate weighted average consensus rate
                    final BigDecimal totalConsensusRate = sessions.stream()
                            .map(session -> {
                                try {
                                    final SessionSummaryStats stats =
                                        objectMapper.readValue(
                                            session.summaryStats,
                                            SessionSummaryStats.class);
                                    return stats.getConsensusRate() != null
                                            ? stats.getConsensusRate()
                                            : BigDecimal.ZERO;
                                } catch (JsonProcessingException e) {
                                    return BigDecimal.ZERO;
                                }
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    final BigDecimal averageConsensusRate =
                            totalSessions > 0
                            ? totalConsensusRate.divide(
                                    BigDecimal.valueOf(totalSessions),
                                    DECIMAL_SCALE, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    return Map.of(
                            "total_sessions", totalSessions,
                            "total_rounds", totalRounds,
                            "total_stories", totalStories,
                            "average_consensus_rate", averageConsensusRate
                    );
                });
    }
}
