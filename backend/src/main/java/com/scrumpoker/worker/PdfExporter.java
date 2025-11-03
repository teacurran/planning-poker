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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates PDF export files for session reports.
 * <p>
 * PDF format includes:
 * <ul>
 *   <li>Title page with session metadata</li>
 *   <li>Summary statistics table (total rounds, consensus rate, average vote)</li>
 *   <li>Round-by-round breakdown with vote details</li>
 * </ul>
 * </p>
 *
 * <p><strong>PDF Structure:</strong></p>
 * <pre>
 *   ========================================
 *   Session Report
 *   Room: User Authentication Module
 *   Session: 2025-01-15 10:30 - 11:45 UTC
 *   ========================================
 *
 *   Summary Statistics:
 *   - Total Stories: 8
 *   - Total Rounds: 10
 *   - Consensus Rate: 75.0%
 *   - Average Vote: 5.67
 *
 *   ========================================
 *   Round Details:
 *
 *   Round 1: User Login
 *     Votes: Alice(5), Bob(5), Carol(3)
 *     Stats: Avg=4.67, Median=5, Consensus=Yes
 *     Time: 2025-01-15 10:35:00 - 10:36:00 UTC
 *   ...
 * </pre>
 *
 * @see ExportJobProcessor
 */
@ApplicationScoped
public class PdfExporter {

    /**
     * Date/time formatter for PDF output (ISO 8601 format without seconds).
     */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.of("UTC"));

    /**
     * Decimal scale for statistical calculations in PDF.
     */
    private static final int DECIMAL_SCALE = 2;

    /**
     * PDF page margins (in points, 72 points = 1 inch).
     */
    private static final float MARGIN_TOP = 50;
    private static final float MARGIN_BOTTOM = 50;
    private static final float MARGIN_LEFT = 50;
    private static final float MARGIN_RIGHT = 50;

    /**
     * Line spacing multiplier.
     */
    private static final float LINE_SPACING = 1.5f;

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
     * Generates a PDF export file for the given session.
     * <p>
     * Queries all rounds and votes for the session, formats data as PDF,
     * and returns the file content as a byte array.
     * </p>
     *
     * @param session The session history record
     * @param sessionId The session UUID
     * @return Uni containing the PDF file content as byte array
     * @throws ExportGenerationException if PDF generation fails
     */
    public Uni<byte[]> generatePdfExport(final SessionHistory session, final UUID sessionId) {
        if (session == null || sessionId == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("session and sessionId cannot be null"));
        }

        Log.infof("Generating PDF export for session %s (room: %s)",
                sessionId, session.room.title);

        // Fetch all rounds for the room and filter by session timeframe
        return roundRepository.findByRoomId(session.room.roomId)
                .onItem().transformToUni(allRounds -> {
                    // Filter rounds by session timeframe
                    final List<Round> sessionRounds = allRounds.stream()
                            .filter(r -> !r.startedAt.isBefore(session.id.startedAt)
                                    && (session.endedAt == null
                                    || !r.startedAt.isAfter(session.endedAt)))
                            .sorted((r1, r2) -> r1.roundNumber.compareTo(r2.roundNumber))
                            .collect(Collectors.toList());

                    // Fetch votes for all rounds
                    return fetchVotesForRounds(sessionRounds)
                            .onItem().transform(roundVoteMap ->
                                    generatePdfContent(session, sessionRounds, roundVoteMap));
                })
                .onFailure().transform(e -> {
                    Log.errorf(e, "Failed to generate PDF export for session %s", sessionId);
                    return new ExportGenerationException(
                            "PDF generation failed: " + e.getMessage(), e);
                });
    }

    /**
     * Fetches votes for all rounds in parallel.
     *
     * @param rounds List of rounds
     * @return Uni containing map of round ID to votes list
     */
    private Uni<Map<UUID, List<Vote>>> fetchVotesForRounds(final List<Round> rounds) {
        if (rounds.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }

        final List<Uni<Map.Entry<UUID, List<Vote>>>> voteFetches = rounds.stream()
                .map(round -> voteRepository.findByRoundId(round.roundId)
                        .onItem().transform(votes -> Map.entry(round.roundId, votes)))
                .collect(Collectors.toList());

        return Uni.combine().all().unis(voteFetches)
                .with(results -> results.stream()
                        .map(obj -> (Map.Entry<UUID, List<Vote>>) obj)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    /**
     * Generates PDF content from session, rounds, and votes.
     *
     * @param session The session history
     * @param rounds List of rounds in the session
     * @param roundVoteMap Map of round ID to votes
     * @return PDF file content as byte array
     */
    private byte[] generatePdfContent(
            final SessionHistory session,
            final List<Round> rounds,
            final Map<UUID, List<Vote>> roundVoteMap) {

        try (final PDDocument document = new PDDocument();
             final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float yPosition = page.getMediaBox().getHeight() - MARGIN_TOP;

            try (PDPageContentStream contentStream = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                // Title
                yPosition = writeTitle(contentStream, session, yPosition);

                // Summary statistics
                yPosition = writeSummaryStats(contentStream, session, rounds, yPosition);

                // Round details
                yPosition = writeRoundDetails(contentStream, document, page,
                        rounds, roundVoteMap, yPosition);
            }

            document.save(outputStream);
            final byte[] pdfBytes = outputStream.toByteArray();

            Log.infof("Generated PDF export: %d bytes, %d pages, %d rounds, session=%s",
                    pdfBytes.length, document.getNumberOfPages(), rounds.size(),
                    session.id.sessionId);

            return pdfBytes;

        } catch (IOException e) {
            Log.errorf(e, "Failed to write PDF content for session %s", session.id.sessionId);
            throw new ExportGenerationException("PDF write failed: " + e.getMessage(), e);
        }
    }

    /**
     * Writes the title section to the PDF.
     *
     * @param contentStream The PDF content stream
     * @param session The session history
     * @param yPosition Current Y position on page
     * @return New Y position after writing
     * @throws IOException if write fails
     */
    private float writeTitle(final PDPageContentStream contentStream,
                             final SessionHistory session,
                             float yPosition) throws IOException {

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 18);
        contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
        contentStream.showText("Session Report");
        contentStream.endText();

        yPosition -= 25;

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
        contentStream.showText("Room: " + session.room.title);
        contentStream.endText();

        yPosition -= 18;

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 12);
        contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
        contentStream.showText("Session: " + formatTimestamp(session.id.startedAt)
                + " - " + (session.endedAt != null
                ? formatTimestamp(session.endedAt)
                : "Ongoing") + " UTC");
        contentStream.endText();

        yPosition -= 30;

        // Draw separator line
        contentStream.setLineWidth(1);
        contentStream.moveTo(MARGIN_LEFT, yPosition);
        contentStream.lineTo(PDRectangle.A4.getWidth() - MARGIN_RIGHT, yPosition);
        contentStream.stroke();

        return yPosition - 20;
    }

    /**
     * Writes the summary statistics section to the PDF.
     *
     * @param contentStream The PDF content stream
     * @param session The session history
     * @param rounds List of rounds
     * @param yPosition Current Y position on page
     * @return New Y position after writing
     * @throws IOException if write fails
     */
    private float writeSummaryStats(final PDPageContentStream contentStream,
                                    final SessionHistory session,
                                    final List<Round> rounds,
                                    float yPosition) throws IOException {

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
        contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
        contentStream.showText("Summary Statistics");
        contentStream.endText();

        yPosition -= 20;

        // Calculate summary stats
        final BigDecimal avgVote = rounds.stream()
                .filter(r -> r.average != null)
                .map(r -> r.average)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(Math.max(1,
                                rounds.stream().filter(r -> r.average != null).count())),
                        DECIMAL_SCALE, RoundingMode.HALF_UP);

        final long consensusCount = rounds.stream()
                .filter(r -> r.consensusReached != null && r.consensusReached)
                .count();

        final BigDecimal consensusRate = rounds.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(consensusCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(rounds.size()),
                        DECIMAL_SCALE, RoundingMode.HALF_UP);

        // Write stats
        final String[] stats = {
                "Total Stories: " + session.totalStories,
                "Total Rounds: " + session.totalRounds,
                "Consensus Rate: " + consensusRate + "%",
                "Average Vote: " + avgVote
        };

        contentStream.setFont(PDType1Font.HELVETICA, 12);
        for (final String stat : stats) {
            contentStream.beginText();
            contentStream.newLineAtOffset(MARGIN_LEFT + 10, yPosition);
            contentStream.showText("- " + stat);
            contentStream.endText();
            yPosition -= 18;
        }

        yPosition -= 10;

        // Draw separator line
        contentStream.setLineWidth(1);
        contentStream.moveTo(MARGIN_LEFT, yPosition);
        contentStream.lineTo(PDRectangle.A4.getWidth() - MARGIN_RIGHT, yPosition);
        contentStream.stroke();

        return yPosition - 20;
    }

    /**
     * Writes the round details section to the PDF.
     *
     * @param contentStream The PDF content stream
     * @param document The PDF document
     * @param currentPage The current page
     * @param rounds List of rounds
     * @param roundVoteMap Map of round ID to votes
     * @param yPosition Current Y position on page
     * @return New Y position after writing
     * @throws IOException if write fails
     */
    private float writeRoundDetails(PDPageContentStream contentStream,
                                    final PDDocument document,
                                    PDPage currentPage,
                                    final List<Round> rounds,
                                    final Map<UUID, List<Vote>> roundVoteMap,
                                    float yPosition) throws IOException {

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 14);
        contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
        contentStream.showText("Round Details");
        contentStream.endText();

        yPosition -= 25;

        if (rounds.isEmpty()) {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 12);
            contentStream.newLineAtOffset(MARGIN_LEFT + 10, yPosition);
            contentStream.showText("No rounds found for this session");
            contentStream.endText();
            return yPosition - 20;
        }

        for (final Round round : rounds) {
            // Check if we need a new page
            if (yPosition < MARGIN_BOTTOM + 100) {
                contentStream.close();
                currentPage = new PDPage(PDRectangle.A4);
                document.addPage(currentPage);
                contentStream = new PDPageContentStream(
                        document, currentPage, PDPageContentStream.AppendMode.APPEND, true, true);
                yPosition = currentPage.getMediaBox().getHeight() - MARGIN_TOP;
            }

            yPosition = writeRoundDetail(contentStream, round,
                    roundVoteMap.getOrDefault(round.roundId, List.of()), yPosition);
        }

        return yPosition;
    }

    /**
     * Writes a single round detail to the PDF.
     *
     * @param contentStream The PDF content stream
     * @param round The round
     * @param votes List of votes for the round
     * @param yPosition Current Y position on page
     * @return New Y position after writing
     * @throws IOException if write fails
     */
    private float writeRoundDetail(final PDPageContentStream contentStream,
                                   final Round round,
                                   final List<Vote> votes,
                                   float yPosition) throws IOException {

        // Round title
        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA_BOLD, 12);
        contentStream.newLineAtOffset(MARGIN_LEFT, yPosition);
        contentStream.showText("Round " + round.roundNumber + ": "
                + (round.storyTitle != null ? round.storyTitle : "Untitled"));
        contentStream.endText();

        yPosition -= 18;

        // Votes
        if (!votes.isEmpty()) {
            final String votesSummary = votes.stream()
                    .map(v -> v.participant.displayName + "(" + v.cardValue + ")")
                    .collect(Collectors.joining(", "));

            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 10);
            contentStream.newLineAtOffset(MARGIN_LEFT + 10, yPosition);
            contentStream.showText("Votes: " + votesSummary);
            contentStream.endText();

            yPosition -= 15;
        }

        // Stats
        final String stats = String.format("Stats: Avg=%s, Median=%s, Consensus=%s",
                round.average != null ? round.average.toString() : "N/A",
                round.median != null ? round.median : "N/A",
                round.consensusReached != null && round.consensusReached ? "Yes" : "No");

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.newLineAtOffset(MARGIN_LEFT + 10, yPosition);
        contentStream.showText(stats);
        contentStream.endText();

        yPosition -= 15;

        // Time
        final String time = String.format("Time: %s - %s UTC",
                formatTimestamp(round.startedAt),
                round.revealedAt != null ? formatTimestamp(round.revealedAt) : "Ongoing");

        contentStream.beginText();
        contentStream.setFont(PDType1Font.HELVETICA, 10);
        contentStream.newLineAtOffset(MARGIN_LEFT + 10, yPosition);
        contentStream.showText(time);
        contentStream.endText();

        return yPosition - 25;
    }

    /**
     * Formats an Instant timestamp to a readable string (UTC).
     *
     * @param timestamp The instant to format
     * @return Formatted timestamp string (yyyy-MM-dd HH:mm UTC)
     */
    private String formatTimestamp(final Instant timestamp) {
        return timestamp != null ? DATE_TIME_FORMATTER.format(timestamp) : "";
    }
}
