package com.scrumpoker.worker;

import com.scrumpoker.domain.room.Round;
import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.room.Vote;
import com.scrumpoker.repository.RoundRepository;
import com.scrumpoker.repository.VoteRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates CSV export files for session reports.
 * <p>
 * CSV format includes:
 * <ul>
 *   <li>Header row with session metadata (room name, date range)</li>
 *   <li>Data rows with round details and individual votes</li>
 *   <li>One row per vote, with round metadata repeated for votes in same round</li>
 * </ul>
 * </p>
 *
 * <p><strong>CSV Structure:</strong></p>
 * <pre>
 *   Session Report,Room Name,2025-01-15 10:30:00,2025-01-15 11:45:00
 *
 *   Round Number,Story Title,Participant Name,Vote,Average,Median,Consensus,Started At,Revealed At
 *   1,User Authentication,Alice,5,4.67,5,true,2025-01-15 10:35:00,2025-01-15 10:36:00
 *   1,User Authentication,Bob,5,4.67,5,true,2025-01-15 10:35:00,2025-01-15 10:36:00
 *   1,User Authentication,Carol,3,4.67,5,true,2025-01-15 10:35:00,2025-01-15 10:36:00
 *   2,Payment Gateway,Alice,13,10.33,8,false,2025-01-15 10:40:00,2025-01-15 10:41:00
 *   ...
 * </pre>
 *
 * @see ExportJobProcessor
 */
@ApplicationScoped
public class CsvExporter {

    /**
     * Date/time formatter for CSV output (ISO 8601 format without timezone).
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("UTC"));

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
     * Generates a CSV export file for the given session.
     * <p>
     * Queries all rounds and votes for the session, formats data as CSV,
     * and returns the file content as a byte array.
     * </p>
     *
     * @param session The session history record
     * @param sessionId The session UUID
     * @return Uni containing the CSV file content as byte array
     * @throws ExportGenerationException if CSV generation fails
     */
    public Uni<byte[]> generateCsvExport(final SessionHistory session, final UUID sessionId) {
        if (session == null || sessionId == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("session and sessionId cannot be null"));
        }

        Log.infof("Generating CSV export for session %s (room: %s)",
                sessionId, session.room.title);

        // Fetch all rounds for the room and filter by session timeframe
        return roundRepository.findByRoomId(session.room.roomId)
                .onItem().transformToUni(allRounds -> {
                    // Filter rounds by session timeframe
                    final List<Round> sessionRounds = allRounds.stream()
                            .filter(r -> !r.startedAt.isBefore(session.id.startedAt)
                                    && (session.endedAt == null
                                    || !r.startedAt.isAfter(session.endedAt)))
                            .collect(Collectors.toList());

                    if (sessionRounds.isEmpty()) {
                        Log.warnf("No rounds found for session %s, generating empty CSV", sessionId);
                        return generateEmptyCsv(session);
                    }

                    // Fetch votes for all rounds
                    return fetchVotesForRounds(sessionRounds)
                            .onItem().transform(roundVoteMap ->
                                    generateCsvContent(session, sessionRounds, roundVoteMap));
                })
                .onFailure().transform(e -> {
                    Log.errorf(e, "Failed to generate CSV export for session %s", sessionId);
                    return new ExportGenerationException(
                            "CSV generation failed: " + e.getMessage(), e);
                });
    }

    /**
     * Fetches votes for all rounds in parallel.
     *
     * @param rounds List of rounds
     * @return Uni containing map of round ID to votes list
     */
    private Uni<Map<UUID, List<Vote>>> fetchVotesForRounds(final List<Round> rounds) {
        // Create a list of Uni items for each round's votes
        final List<Uni<Map.Entry<UUID, List<Vote>>>> voteFetches = rounds.stream()
                .map(round -> voteRepository.findByRoundId(round.roundId)
                        .onItem().transform(votes -> Map.entry(round.roundId, votes)))
                .collect(Collectors.toList());

        // Combine all Uni items and collect into a map
        return Uni.combine().all().unis(voteFetches)
                .with(results -> results.stream()
                        .map(obj -> (Map.Entry<UUID, List<Vote>>) obj)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Generates CSV content from session, rounds, and votes.
     *
     * @param session The session history
     * @param rounds List of rounds in the session
     * @param roundVoteMap Map of round ID to votes
     * @return CSV file content as byte array
     */
    private byte[] generateCsvContent(
            final SessionHistory session,
            final List<Round> rounds,
            final Map<UUID, List<Vote>> roundVoteMap) {

        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             final OutputStreamWriter writer = new OutputStreamWriter(
                     outputStream, StandardCharsets.UTF_8);
             final CSVPrinter csvPrinter = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader("Round Number", "Story Title", "Participant Name",
                                     "Vote", "Average", "Median", "Consensus",
                                     "Started At", "Revealed At")
                             .build())) {

            // Write session metadata header
            csvPrinter.printComment(String.format("Session Report: %s", session.room.title));
            csvPrinter.printComment(String.format("Session Period: %s to %s",
                    formatTimestamp(session.id.startedAt),
                    session.endedAt != null ? formatTimestamp(session.endedAt) : "Ongoing"));
            csvPrinter.printComment(String.format("Total Stories: %d", session.totalStories));
            csvPrinter.printComment(String.format("Total Rounds: %d", session.totalRounds));
            csvPrinter.println();

            // Write data rows (one row per vote)
            for (final Round round : rounds) {
                final List<Vote> votes = roundVoteMap.getOrDefault(round.roundId, List.of());

                if (votes.isEmpty()) {
                    // Write round with no votes
                    csvPrinter.printRecord(
                            round.roundNumber,
                            round.storyTitle != null ? round.storyTitle : "",
                            "",  // No participant
                            "",  // No vote
                            round.average != null ? round.average.toString() : "",
                            round.median != null ? round.median : "",
                            round.consensusReached != null ? round.consensusReached : false,
                            formatTimestamp(round.startedAt),
                            round.revealedAt != null ? formatTimestamp(round.revealedAt) : ""
                    );
                } else {
                    // Write one row per vote
                    for (final Vote vote : votes) {
                        csvPrinter.printRecord(
                                round.roundNumber,
                                round.storyTitle != null ? round.storyTitle : "",
                                vote.participant.displayName,
                                vote.cardValue,
                                round.average != null ? round.average.toString() : "",
                                round.median != null ? round.median : "",
                                round.consensusReached != null ? round.consensusReached : false,
                                formatTimestamp(round.startedAt),
                                round.revealedAt != null ? formatTimestamp(round.revealedAt) : ""
                        );
                    }
                }
            }

            csvPrinter.flush();
            final byte[] csvBytes = outputStream.toByteArray();

            Log.infof("Generated CSV export: %d bytes, %d rounds, session=%s",
                    csvBytes.length, rounds.size(), session.id.sessionId);

            return csvBytes;

        } catch (IOException e) {
            Log.errorf(e, "Failed to write CSV content for session %s", session.id.sessionId);
            throw new ExportGenerationException("CSV write failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generates an empty CSV file with session metadata only.
     *
     * @param session The session history
     * @return Uni containing empty CSV as byte array
     */
    private Uni<byte[]> generateEmptyCsv(final SessionHistory session) {
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             final OutputStreamWriter writer = new OutputStreamWriter(
                     outputStream, StandardCharsets.UTF_8);
             final CSVPrinter csvPrinter = new CSVPrinter(writer,
                     CSVFormat.DEFAULT.builder()
                             .setHeader("Round Number", "Story Title", "Participant Name",
                                     "Vote", "Average", "Median", "Consensus",
                                     "Started At", "Revealed At")
                             .build())) {

            csvPrinter.printComment(String.format("Session Report: %s", session.room.title));
            csvPrinter.printComment(String.format("Session Period: %s to %s",
                    formatTimestamp(session.id.startedAt),
                    session.endedAt != null ? formatTimestamp(session.endedAt) : "Ongoing"));
            csvPrinter.printComment("No rounds found for this session");
            csvPrinter.println();

            csvPrinter.flush();
            return Uni.createFrom().item(outputStream.toByteArray());

        } catch (IOException e) {
            return Uni.createFrom().failure(
                    new ExportGenerationException("Failed to generate empty CSV: " + e.getMessage(), e));
        }
    }

    /**
     * Formats an Instant timestamp to a readable string (UTC).
     *
     * @param timestamp The instant to format
     * @return Formatted timestamp string (yyyy-MM-dd HH:mm:ss UTC)
     */
    private String formatTimestamp(final Instant timestamp) {
        return timestamp != null ? DATE_TIME_FORMATTER.format(timestamp) : "";
    }
}
