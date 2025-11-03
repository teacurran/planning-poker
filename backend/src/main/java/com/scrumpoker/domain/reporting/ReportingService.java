package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.domain.room.Round;
import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.Vote;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.repository.RoundRepository;
import com.scrumpoker.repository.VoteRepository;
import com.scrumpoker.security.FeatureGate;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.ReactiveStreamCommands;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for generating tier-based session reports and analytics.
 * <p>
 * Provides three main capabilities:
 * <ul>
 *   <li><strong>Basic summaries</strong> (Free tier): Story count, consensus rate, average vote</li>
 *   <li><strong>Detailed analytics</strong> (Pro tier): Round-by-round breakdown, individual votes, user consistency</li>
 *   <li><strong>Export job enqueuing</strong> (Pro tier): CSV/PDF generation via Redis Streams</li>
 * </ul>
 * </p>
 *
 * <p><strong>Tier Enforcement:</strong></p>
 * <p>
 * This service uses {@link FeatureGate} to enforce subscription tier requirements.
 * Free tier users can only access basic summaries. Attempting to access detailed
 * reports or export functionality will result in a {@code FeatureNotAvailableException}
 * which translates to a 403 HTTP response.
 * </p>
 *
 * @see SessionHistoryService
 * @see FeatureGate
 * @see SessionSummaryDTO
 * @see DetailedSessionReportDTO
 */
@ApplicationScoped
public class ReportingService {

    /**
     * Default decimal scale for statistical calculations.
     */
    private static final int DECIMAL_SCALE = 4;

    /**
     * Redis Stream key for export jobs.
     */
    private static final String EXPORT_JOBS_STREAM = "jobs:reports";

    /**
     * Service for querying session history records.
     */
    @Inject
    private SessionHistoryService sessionHistoryService;

    /**
     * Feature gate for tier-based access control.
     */
    @Inject
    private FeatureGate featureGate;

    /**
     * Repository for querying rounds.
     */
    @Inject
    private RoundRepository roundRepository;

    /**
     * Repository for querying votes.
     */
    @Inject
    private VoteRepository voteRepository;

    /**
     * Reactive Redis data source for stream operations.
     */
    @Inject
    private ReactiveRedisDataSource redisDataSource;

    /**
     * JSON object mapper for deserializing JSONB fields.
     */
    @Inject
    private ObjectMapper objectMapper;

    /**
     * Generates a basic session summary report (Free tier).
     * <p>
     * Returns high-level session metrics without detailed round-by-round data:
     * <ul>
     *   <li>Total stories estimated</li>
     *   <li>Total rounds (including re-votes)</li>
     *   <li>Consensus rate (percentage of rounds with agreement)</li>
     *   <li>Average vote across all rounds</li>
     *   <li>Participant count</li>
     *   <li>Total votes cast</li>
     * </ul>
     * </p>
     *
     * <p><strong>Tier Requirement:</strong> None (available to all tiers)</p>
     *
     * @param sessionId The session UUID
     * @return Uni containing the basic session summary
     */
    public Uni<SessionSummaryDTO> getBasicSessionSummary(final UUID sessionId) {
        if (sessionId == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("sessionId cannot be null"));
        }

        return sessionHistoryService.getSessionById(sessionId)
                .onItem().ifNull().failWith(() ->
                        new IllegalArgumentException(
                                "Session not found: " + sessionId))
                .onItem().transformToUni(this::buildBasicSummary);
    }

    /**
     * Generates a detailed session report with round-by-round breakdown (Pro tier).
     * <p>
     * Returns comprehensive session analytics including:
     * <ul>
     *   <li>All fields from basic summary</li>
     *   <li>Round-by-round voting details with individual votes</li>
     *   <li>User consistency metrics (standard deviation of votes per participant)</li>
     * </ul>
     * </p>
     *
     * <p><strong>Tier Requirement:</strong> PRO or higher</p>
     *
     * <p><strong>Non-Numeric Vote Handling:</strong></p>
     * <p>
     * Special card values (?, ∞, ☕) are included in round details but excluded
     * from statistical calculations (average, user consistency). This is documented
     * in the response and reflected in the calculations.
     * </p>
     *
     * @param sessionId The session UUID
     * @param user The requesting user (for tier enforcement)
     * @return Uni containing the detailed session report
     * @throws com.scrumpoker.security.FeatureNotAvailableException if user lacks PRO tier
     */
    public Uni<DetailedSessionReportDTO> getDetailedSessionReport(
            final UUID sessionId, final User user) {
        if (sessionId == null || user == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "sessionId and user cannot be null"));
        }

        // Enforce tier requirement (throws exception if Free tier)
        featureGate.requireCanAccessAdvancedReports(user);

        return sessionHistoryService.getSessionById(sessionId)
                .onItem().ifNull().failWith(() ->
                        new IllegalArgumentException(
                                "Session not found: " + sessionId))
                .onItem().transformToUni(session ->
                        buildDetailedReport(session, sessionId));
    }

    /**
     * Enqueues an export job for CSV/PDF generation (Pro tier).
     * <p>
     * Creates a job message in the Redis Stream {@code jobs:reports} which will
     * be consumed by a background worker process. The worker will generate the
     * requested export file and store it in object storage for user download.
     * </p>
     *
     * <p><strong>Tier Requirement:</strong> PRO or higher</p>
     *
     * <p><strong>Supported Formats:</strong></p>
     * <ul>
     *   <li>CSV: Tabular format with round details and votes</li>
     *   <li>PDF: Formatted report with charts and summary tables</li>
     * </ul>
     *
     * @param sessionId The session UUID to export
     * @param format The export format ("CSV" or "PDF")
     * @param user The requesting user (for tier enforcement)
     * @return Uni containing the Redis Stream message ID (job ID)
     * @throws com.scrumpoker.security.FeatureNotAvailableException if user lacks PRO tier
     */
    public Uni<String> generateExport(final UUID sessionId,
                                       final String format,
                                       final User user) {
        if (sessionId == null || format == null || user == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "sessionId, format, and user cannot be null"));
        }

        // Enforce tier requirement (throws exception if Free tier)
        featureGate.requireCanAccessAdvancedReports(user);

        // Validate format
        if (!format.equals("CSV") && !format.equals("PDF")) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Format must be 'CSV' or 'PDF'"));
        }

        // Verify session exists before enqueuing job
        return sessionHistoryService.getSessionById(sessionId)
                .onItem().ifNull().failWith(() ->
                        new IllegalArgumentException(
                                "Session not found: " + sessionId))
                .onItem().transformToUni(session ->
                        enqueueExportJob(sessionId, format, user));
    }

    /**
     * Builds a basic session summary from SessionHistory entity.
     *
     * @param session The session history record
     * @return Uni containing the session summary DTO
     */
    private Uni<SessionSummaryDTO> buildBasicSummary(
            final SessionHistory session) {
        try {
            // Deserialize JSONB fields
            final SessionSummaryStats stats = objectMapper.readValue(
                    session.summaryStats, SessionSummaryStats.class);

            final List<ParticipantSummary> participants =
                    objectMapper.readValue(
                            session.participants,
                            objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, ParticipantSummary.class));

            // Calculate overall average vote across all rounds
            final String roomId = session.room.roomId;
            return roundRepository.findByRoomId(roomId)
                    .onItem().transform(rounds -> {
                        // Filter rounds by session timeframe
                        final List<Round> sessionRounds = rounds.stream()
                                .filter(r -> !r.startedAt.isBefore(
                                        session.id.startedAt)
                                        && (session.endedAt == null
                                        || !r.startedAt.isAfter(
                                        session.endedAt)))
                                .collect(Collectors.toList());

                        // Calculate average of round averages
                        final BigDecimal averageVote = sessionRounds.stream()
                                .filter(r -> r.average != null)
                                .map(r -> r.average)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .divide(BigDecimal.valueOf(
                                        Math.max(1, sessionRounds.stream()
                                                .filter(r -> r.average != null)
                                                .count())),
                                        DECIMAL_SCALE, RoundingMode.HALF_UP);

                        return new SessionSummaryDTO(
                                session.id.sessionId,
                                session.room.title,
                                session.id.startedAt,
                                session.endedAt,
                                session.totalStories,
                                session.totalRounds,
                                stats.getConsensusRate() != null
                                        ? stats.getConsensusRate()
                                        : BigDecimal.ZERO,
                                averageVote,
                                participants.size(),
                                stats.getTotalVotes() != null
                                        ? stats.getTotalVotes()
                                        : 0
                        );
                    });
        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to deserialize JSONB fields for session %s",
                    session.id.sessionId);
            return Uni.createFrom().failure(
                    new RuntimeException("Failed to parse session data", e));
        }
    }

    /**
     * Builds a detailed session report with round-by-round breakdown and
     * user consistency metrics.
     *
     * @param session The session history record
     * @param sessionId The session UUID
     * @return Uni containing the detailed report DTO
     */
    private Uni<DetailedSessionReportDTO> buildDetailedReport(
            final SessionHistory session, final UUID sessionId) {
        try {
            // Deserialize JSONB fields
            final SessionSummaryStats stats = objectMapper.readValue(
                    session.summaryStats, SessionSummaryStats.class);

            final List<ParticipantSummary> participants =
                    objectMapper.readValue(
                            session.participants,
                            objectMapper.getTypeFactory().constructCollectionType(
                                    List.class, ParticipantSummary.class));

            final String roomId = session.room.roomId;

            // Fetch all rounds for the room and filter by session timeframe
            return roundRepository.findByRoomId(roomId)
                    .onItem().transformToUni(allRounds -> {
                        // Filter rounds by session timeframe
                        final List<Round> sessionRounds = allRounds.stream()
                                .filter(r -> !r.startedAt.isBefore(
                                        session.id.startedAt)
                                        && (session.endedAt == null
                                        || !r.startedAt.isAfter(
                                        session.endedAt)))
                                .collect(Collectors.toList());

                        if (sessionRounds.isEmpty()) {
                            // No rounds - return report with empty rounds list
                            return buildEmptyDetailedReport(
                                    session, stats, participants);
                        }

                        // Fetch votes for all rounds in parallel
                        return Multi.createFrom().iterable(sessionRounds)
                                .onItem().transformToUniAndConcatenate(round ->
                                        voteRepository.findByRoundId(
                                                round.roundId)
                                                .onItem().transform(votes ->
                                                        Map.entry(round, votes)))
                                .collect().asList()
                                .onItem().transform(roundVotePairs ->
                                        buildDetailedReportFromRounds(
                                                session, stats, participants,
                                                roundVotePairs));
                    });
        } catch (JsonProcessingException e) {
            Log.errorf(e, "Failed to deserialize JSONB fields for session %s",
                    sessionId);
            return Uni.createFrom().failure(
                    new RuntimeException("Failed to parse session data", e));
        }
    }

    /**
     * Builds a detailed report when no rounds are present.
     *
     * @param session The session history
     * @param stats Session summary statistics
     * @param participants Participant list
     * @return Uni containing empty detailed report
     */
    private Uni<DetailedSessionReportDTO> buildEmptyDetailedReport(
            final SessionHistory session,
            final SessionSummaryStats stats,
            final List<ParticipantSummary> participants) {
        return Uni.createFrom().item(new DetailedSessionReportDTO(
                session.id.sessionId,
                session.room.title,
                session.id.startedAt,
                session.endedAt,
                session.totalStories,
                session.totalRounds,
                stats.getConsensusRate() != null
                        ? stats.getConsensusRate()
                        : BigDecimal.ZERO,
                BigDecimal.ZERO,
                participants.size(),
                stats.getTotalVotes() != null ? stats.getTotalVotes() : 0,
                new ArrayList<>(),
                new HashMap<>()
        ));
    }

    /**
     * Builds a detailed report from rounds and votes.
     *
     * @param session The session history
     * @param stats Session summary statistics
     * @param participants Participant list
     * @param roundVotePairs Round-vote pairs
     * @return Detailed session report DTO
     */
    private DetailedSessionReportDTO buildDetailedReportFromRounds(
            final SessionHistory session,
            final SessionSummaryStats stats,
            final List<ParticipantSummary> participants,
            final List<Map.Entry<Round, List<Vote>>> roundVotePairs) {

        // Build participant ID to name map
        final Map<UUID, String> participantNames = participants.stream()
                .collect(Collectors.toMap(
                        ParticipantSummary::getParticipantId,
                        ParticipantSummary::getDisplayName,
                        (name1, name2) -> name1  // Keep first if duplicate
                ));

        // Build round detail DTOs
        final List<DetailedSessionReportDTO.RoundDetailDTO> roundDetails =
                new ArrayList<>();

        // Collect all votes by participant for consistency calculation
        final Map<UUID, List<String>> votesByParticipant = new HashMap<>();

        for (Map.Entry<Round, List<Vote>> entry : roundVotePairs) {
            final Round round = entry.getKey();
            final List<Vote> votes = entry.getValue();

            // Build vote detail DTOs
            final List<DetailedSessionReportDTO.VoteDetailDTO> voteDetails =
                    votes.stream()
                            .map(vote -> {
                                final UUID participantId =
                                        vote.participant.participantId;
                                final String participantName =
                                        participantNames.getOrDefault(
                                                participantId,
                                                vote.participant.displayName);

                                // Collect votes for consistency calculation
                                votesByParticipant
                                        .computeIfAbsent(participantId,
                                                k -> new ArrayList<>())
                                        .add(vote.cardValue);

                                return new DetailedSessionReportDTO
                                        .VoteDetailDTO(
                                        participantId,
                                        participantName,
                                        vote.cardValue,
                                        vote.votedAt
                                );
                            })
                            .collect(Collectors.toList());

            roundDetails.add(new DetailedSessionReportDTO.RoundDetailDTO(
                    round.roundNumber,
                    round.storyTitle,
                    voteDetails,
                    round.average,
                    round.median,
                    round.consensusReached,
                    round.startedAt,
                    round.revealedAt
            ));
        }

        // Calculate user consistency metrics
        final Map<String, BigDecimal> userConsistency =
                calculateUserConsistency(votesByParticipant, participantNames);

        // Calculate overall average vote
        final BigDecimal averageVote = roundVotePairs.stream()
                .map(Map.Entry::getKey)
                .filter(r -> r.average != null)
                .map(r -> r.average)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(
                        Math.max(1, roundVotePairs.stream()
                                .map(Map.Entry::getKey)
                                .filter(r -> r.average != null)
                                .count())),
                        DECIMAL_SCALE, RoundingMode.HALF_UP);

        return new DetailedSessionReportDTO(
                session.id.sessionId,
                session.room.title,
                session.id.startedAt,
                session.endedAt,
                session.totalStories,
                session.totalRounds,
                stats.getConsensusRate() != null
                        ? stats.getConsensusRate()
                        : BigDecimal.ZERO,
                averageVote,
                participants.size(),
                stats.getTotalVotes() != null ? stats.getTotalVotes() : 0,
                roundDetails,
                userConsistency
        );
    }

    /**
     * Calculates user consistency metrics (standard deviation of votes).
     * <p>
     * For each participant, computes the standard deviation of their numeric
     * votes across all rounds. Non-numeric votes (?, ∞, ☕) are excluded.
     * Participants with fewer than 2 numeric votes are excluded from results.
     * </p>
     *
     * <p><strong>Interpretation:</strong></p>
     * <ul>
     *   <li>Lower σ (std dev) = more consistent voting</li>
     *   <li>Higher σ = more variable voting</li>
     *   <li>σ = 0 = perfectly consistent (always voted same value)</li>
     * </ul>
     *
     * @param votesByParticipant Map of participant ID to list of card values
     * @param participantNames Map of participant ID to display name
     * @return Map of participant name to standard deviation
     */
    private Map<String, BigDecimal> calculateUserConsistency(
            final Map<UUID, List<String>> votesByParticipant,
            final Map<UUID, String> participantNames) {

        final Map<String, BigDecimal> consistency = new HashMap<>();

        for (Map.Entry<UUID, List<String>> entry
                : votesByParticipant.entrySet()) {
            final UUID participantId = entry.getKey();
            final List<String> votes = entry.getValue();

            // Filter to numeric votes only
            final List<Double> numericVotes = votes.stream()
                    .map(this::parseNumericVote)
                    .filter(opt -> opt != null)
                    .collect(Collectors.toList());

            // Need at least 2 numeric votes for meaningful std dev
            if (numericVotes.size() < 2) {
                continue;
            }

            // Calculate mean
            final double mean = numericVotes.stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            // Calculate variance
            final double variance = numericVotes.stream()
                    .mapToDouble(vote -> Math.pow(vote - mean, 2))
                    .average()
                    .orElse(0.0);

            // Calculate standard deviation
            final double stdDev = Math.sqrt(variance);

            final String participantName = participantNames.getOrDefault(
                    participantId, "Unknown");

            consistency.put(participantName,
                    BigDecimal.valueOf(stdDev)
                            .setScale(DECIMAL_SCALE, RoundingMode.HALF_UP));
        }

        return consistency;
    }

    /**
     * Parses a card value to a numeric Double.
     * Returns null for non-numeric values (?, ∞, ☕).
     *
     * @param cardValue The card value string
     * @return Parsed Double or null if non-numeric
     */
    private Double parseNumericVote(final String cardValue) {
        if (cardValue == null || cardValue.isEmpty()) {
            return null;
        }

        try {
            return Double.parseDouble(cardValue);
        } catch (NumberFormatException e) {
            // Non-numeric card (?, ∞, ☕)
            return null;
        }
    }

    /**
     * Enqueues an export job to the Redis Stream.
     *
     * @param sessionId The session UUID
     * @param format Export format (CSV or PDF)
     * @param user The requesting user
     * @return Uni containing the Redis Stream message ID
     */
    private Uni<String> enqueueExportJob(final UUID sessionId,
                                          final String format,
                                          final User user) {
        final String jobId = UUID.randomUUID().toString();

        final Map<String, String> jobData = Map.of(
                "jobId", jobId,
                "sessionId", sessionId.toString(),
                "format", format,
                "userId", user.userId.toString(),
                "requestedAt", Instant.now().toString()
        );

        final ReactiveStreamCommands<String, String, String> streamCommands =
                redisDataSource.stream(String.class, String.class, String.class);

        return streamCommands.xadd(EXPORT_JOBS_STREAM, jobData)
                .onItem().invoke(messageId ->
                        Log.infof("Enqueued export job %s for session %s "
                                        + "(format: %s, messageId: %s)",
                                jobId, sessionId, format, messageId))
                .onItem().transform(messageId -> jobId)
                .onFailure().invoke(failure ->
                        Log.errorf(failure,
                                "Failed to enqueue export job for session %s",
                                sessionId));
    }
}
