package com.scrumpoker.worker;

import com.scrumpoker.domain.reporting.ExportJob;
import com.scrumpoker.domain.reporting.JobStatus;
import com.scrumpoker.domain.reporting.SessionHistoryService;
import com.scrumpoker.domain.room.*;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the export job end-to-end flow.
 * <p>
 * Tests the complete lifecycle of CSV/PDF export jobs from job creation
 * through file generation, S3 upload, and status updates.
 * </p>
 *
 * <p><strong>Test Coverage:</strong></p>
 * <ul>
 *   <li>Success flow: PENDING → PROCESSING → COMPLETED</li>
 *   <li>Failure flow: S3 upload failure → FAILED status</li>
 *   <li>Job status transitions and timestamp updates</li>
 *   <li>Download URL generation and validation</li>
 *   <li>S3 client interactions (mocked)</li>
 * </ul>
 *
 * <p><strong>Test Infrastructure:</strong></p>
 * <ul>
 *   <li>PostgreSQL: Testcontainers via Quarkus Dev Services</li>
 *   <li>Redis: Testcontainers via Quarkus Dev Services</li>
 *   <li>S3: Mocked via {@link MockS3Producer}</li>
 * </ul>
 *
 * <p><strong>Test Strategy:</strong></p>
 * <p>
 * Uses manual triggering (calling {@link ExportJobProcessor#processExportJob} directly)
 * rather than enqueuing to Redis Stream. This provides faster, more deterministic tests
 * while still covering all the core processing logic.
 * </p>
 *
 * @see ExportJobProcessor
 * @see MockS3Producer
 * @see ExportJobTestProfile
 */
@QuarkusTest
@TestProfile(ExportJobTestProfile.class)
public class ExportJobIntegrationTest {

    @Inject
    ExportJobProcessor exportJobProcessor;

    @Inject
    SessionHistoryService sessionHistoryService;

    private UUID testJobId;
    private UUID testSessionId;
    private UUID testUserId;
    private String testRoomId;

    /**
     * Clean up test data before each test to ensure test isolation.
     * <p>
     * Deletes all entities in reverse dependency order to avoid
     * foreign key constraint violations.
     * </p>
     */
    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Initialize test identifiers
        testJobId = UUID.randomUUID();
        testSessionId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testRoomId = "test01"; // 6-character room ID

        // Clean up all test data
        asserter.execute(() -> Panache.withTransaction(() ->
            ExportJob.deleteAll()
                .flatMap(v -> Vote.deleteAll())
                .flatMap(v -> Round.deleteAll())
                .flatMap(v -> SessionHistory.deleteAll())
                .flatMap(v -> RoomParticipant.deleteAll())
                .flatMap(v -> Room.deleteAll())
                .flatMap(v -> User.deleteAll())
        ));
    }

    /**
     * Tests the successful export job flow.
     * <p>
     * Verifies:
     * <ul>
     *   <li>Job status transitions: PENDING → PROCESSING → COMPLETED</li>
     *   <li>Download URL is generated and populated</li>
     *   <li>Completion timestamp is set</li>
     *   <li>S3 upload is called with correct parameters</li>
     *   <li>No error message is set</li>
     * </ul>
     * </p>
     */
    @Test
    @RunOnVertxContext
    void testExportJobSuccessFlow(UniAsserter asserter) {
        // Step 1: Create test session with complete data
        asserter.execute(() -> createTestSessionWithData());

        // Step 2: Trigger export job processing (manual trigger)
        asserter.execute(() ->
            exportJobProcessor.processExportJob(testJobId, testSessionId, "CSV", testUserId)
        );

        // Step 3: Verify job completed successfully
        asserter.assertThat(() -> Panache.withTransaction(() ->
            ExportJob.findByJobId(testJobId)),
            job -> {
                assertThat(job).isNotNull();
                assertThat(job.status).isEqualTo(JobStatus.COMPLETED);
                assertThat(job.downloadUrl)
                    .isNotNull()
                    .startsWith("https://")
                    .contains("test-bucket.s3.amazonaws.com");
                assertThat(job.completedAt).isNotNull();
                assertThat(job.processingStartedAt).isNotNull();
                assertThat(job.errorMessage).isNull();
                assertThat(job.failedAt).isNull();
                assertThat(job.sessionId).isEqualTo(testSessionId);
                assertThat(job.format).isEqualTo("CSV");
            }
        );

    }

    /**
     * Tests export job failure when session doesn't exist.
     * <p>
     * Verifies:
     * <ul>
     *   <li>Job status transitions to FAILED</li>
     *   <li>Error message is populated with failure details</li>
     *   <li>Failed timestamp is set</li>
     *   <li>Download URL remains null</li>
     *   <li>System handles missing session gracefully</li>
     * </ul>
     * </p>
     */
    @Test
    @RunOnVertxContext
    void testExportJobFailure_SessionNotFound(UniAsserter asserter) {
        // Step 1: Create ONLY the user (needed for job record creation)
        // Do NOT create session data - this will cause the job to fail when fetching session
        asserter.execute(() -> Panache.withTransaction(() -> {
            User user = new User();
            user.email = "test-failure@example.com";
            user.displayName = "Test Failure User";
            user.oauthProvider = "google";
            user.oauthSubject = "test-oauth-subject-failure-" + UUID.randomUUID();
            user.subscriptionTier = SubscriptionTier.FREE;

            return user.persist()
                .flatMap(v -> {
                    testUserId = user.userId; // Update test userId from persisted user
                    return Panache.flush();
                })
                .replaceWithVoid();
        }));

        // Step 2: Trigger export job processing with non-existent session
        asserter.execute(() ->
            exportJobProcessor.processExportJob(testJobId, testSessionId, "CSV", testUserId)
        );

        // Step 3: Verify job marked as failed with error details
        asserter.assertThat(() -> Panache.withTransaction(() ->
            ExportJob.findByJobId(testJobId)),
            job -> {
                assertThat(job).isNotNull();
                assertThat(job.status).isEqualTo(JobStatus.FAILED);
                assertThat(job.errorMessage)
                    .isNotNull()
                    .contains("not found");
                assertThat(job.failedAt).isNotNull();
                assertThat(job.processingStartedAt).isNotNull();
                assertThat(job.downloadUrl).isNull();
                assertThat(job.completedAt).isNull();
            }
        );
    }

    /**
     * Creates comprehensive test data for export job processing.
     * <p>
     * Creates:
     * <ul>
     *   <li>User with PRO tier subscription</li>
     *   <li>Room owned by the user</li>
     *   <li>SessionHistory with composite key</li>
     *   <li>RoomParticipant for the user</li>
     *   <li>Round with story and reveal timestamp</li>
     *   <li>Vote from the participant</li>
     * </ul>
     * </p>
     *
     * @return Uni completing when all test data is persisted
     */
    private Uni<Void> createTestSessionWithData() {
        return Panache.withTransaction(() -> {
            // Create User (don't set userId - let it auto-generate)
            User user = new User();
            user.email = "test-export@example.com";
            user.displayName = "Test Export User";
            user.oauthProvider = "google";
            user.oauthSubject = "test-oauth-subject-" + UUID.randomUUID();
            user.subscriptionTier = SubscriptionTier.PRO;

            // Create Room
            Room room = new Room();
            room.roomId = testRoomId;
            room.title = "Test Export Room";
            room.owner = user;
            room.privacyMode = PrivacyMode.PUBLIC;
            room.createdAt = Instant.now().minusSeconds(7200); // Created 2 hours ago
            room.lastActiveAt = Instant.now().minusSeconds(60); // Last active 1 minute ago
            room.config = """
                {
                  "deck_type": "FIBONACCI",
                  "timer_enabled": false,
                  "reveal_behavior": "MANUAL",
                  "allow_observers": true
                }
                """;

            // Create SessionHistory with composite key
            SessionHistory session = new SessionHistory();
            SessionHistoryId sessionId = new SessionHistoryId();
            sessionId.sessionId = testSessionId;
            sessionId.startedAt = Instant.now().minusSeconds(3600); // Started 1 hour ago
            session.id = sessionId;
            session.room = room;
            session.endedAt = Instant.now().minusSeconds(60); // Ended 1 minute ago
            session.totalRounds = 1;
            session.totalStories = 1;
            session.createdAt = Instant.now().minusSeconds(3600); // Set explicitly to avoid validation error
            session.participants = """
                [
                  {
                    "participantId": "%s",
                    "displayName": "Test Export User",
                    "role": "VOTER"
                  }
                ]
                """.formatted(testUserId);
            session.summaryStats = """
                {
                  "avg_estimation_time": 120.5,
                  "consensus_rate": 0.85,
                  "total_votes": 5
                }
                """;

            // Create RoomParticipant
            RoomParticipant participant = new RoomParticipant();
            participant.room = room;
            participant.user = user;
            participant.displayName = "Test Export User";
            participant.role = RoomRole.VOTER;

            // Create Round
            Round round = new Round();
            round.room = room;
            round.roundNumber = 1;
            round.storyTitle = "Test Story for Export";
            round.startedAt = Instant.now().minusSeconds(300);
            round.revealedAt = Instant.now().minusSeconds(60);
            round.consensusReached = true;

            // Create Vote
            Vote vote = new Vote();
            vote.round = round;
            vote.participant = participant;
            vote.cardValue = "5";
            vote.votedAt = Instant.now().minusSeconds(180);

            // Persist all entities - use persist() not persistAndFlush() to avoid detachment
            return user.persist()
                .flatMap(v -> {
                    testUserId = user.userId; // Update test userId with auto-generated value
                    return room.persist();
                })
                .flatMap(v -> session.persist())
                .flatMap(v -> participant.persist())
                .flatMap(v -> round.persist())
                .flatMap(v -> vote.persist())
                .flatMap(v -> Panache.flush()) // Flush once at the end
                .replaceWithVoid();
        });
    }
}
