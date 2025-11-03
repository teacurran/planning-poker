package com.scrumpoker.worker;

import com.scrumpoker.domain.reporting.ExportJob;
import com.scrumpoker.domain.reporting.JobStatus;
import com.scrumpoker.domain.reporting.SessionHistoryService;
import com.scrumpoker.domain.room.SessionHistory;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.integration.s3.S3Adapter;
import com.scrumpoker.integration.s3.S3UploadException;
import io.quarkus.logging.Log;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.*;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.faulttolerance.api.ExponentialBackoff;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Background worker that consumes export jobs from Redis Stream and processes them.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Consume job messages from {@code jobs:reports} Redis Stream</li>
 *   <li>Query session data from PostgreSQL</li>
 *   <li>Generate CSV or PDF file using {@link CsvExporter} or {@link PdfExporter}</li>
 *   <li>Upload file to S3 bucket using {@link S3Adapter}</li>
 *   <li>Generate time-limited signed URL (7-day expiration)</li>
 *   <li>Update job status in database (PENDING → PROCESSING → COMPLETED/FAILED)</li>
 *   <li>Handle errors with exponential backoff retry for transient failures</li>
 * </ul>
 * </p>
 *
 * <p><strong>Redis Stream Consumer Group:</strong></p>
 * <pre>
 *   Stream: jobs:reports
 *   Consumer Group: export-workers
 *   Consumer Name: {hostname}-{uuid}
 * </pre>
 *
 * <p><strong>Error Handling:</strong></p>
 * <ul>
 *   <li>Transient errors (S3 timeout, DB connection): Retry with exponential backoff</li>
 *   <li>Permanent errors (invalid session, data corruption): Mark job FAILED immediately</li>
 *   <li>Max retries (5 attempts): Mark job FAILED with error message</li>
 * </ul>
 *
 * @see ExportJob
 * @see CsvExporter
 * @see PdfExporter
 * @see S3Adapter
 */
@ApplicationScoped
public class ExportJobProcessor {

    /**
     * Redis Stream key for export jobs.
     */
    private static final String EXPORT_JOBS_STREAM = "jobs:reports";

    /**
     * Consumer group name for export workers.
     */
    private static final String CONSUMER_GROUP = "export-workers";

    /**
     * Block duration when reading from stream (wait up to 5 seconds for new messages).
     */
    private static final Duration BLOCK_DURATION = Duration.ofSeconds(5);

    /**
     * Batch size for reading messages from stream.
     */
    private static final int BATCH_SIZE = 10;

    /**
     * Session history service for querying session data.
     */
    @Inject
    private SessionHistoryService sessionHistoryService;

    /**
     * CSV exporter for generating CSV files.
     */
    @Inject
    private CsvExporter csvExporter;

    /**
     * PDF exporter for generating PDF files.
     */
    @Inject
    private PdfExporter pdfExporter;

    /**
     * S3 adapter for uploading files and generating signed URLs.
     */
    @Inject
    private S3Adapter s3Adapter;

    /**
     * Reactive Redis data source for stream operations.
     */
    @Inject
    private ReactiveRedisDataSource redisDataSource;

    /**
     * Redis stream commands for consuming messages.
     */
    private ReactiveStreamCommands<String, String, String> streamCommands;

    /**
     * Consumer name (unique identifier for this worker instance).
     */
    private String consumerName;

    /**
     * Flag to control consumer loop.
     */
    private volatile boolean running = false;

    /**
     * Consumer subscription handle for cancellation.
     */
    private Cancellable consumerSubscription;

    /**
     * Initializes the Redis Stream consumer on application startup.
     * <p>
     * Creates consumer group if it doesn't exist and starts consuming messages.
     * </p>
     *
     * @param event Startup event
     */
    void onStart(@Observes final StartupEvent event) {
        Log.info("Initializing Export Job Processor...");

        // Initialize stream commands
        streamCommands = redisDataSource.stream(String.class, String.class, String.class);

        // Generate unique consumer name
        consumerName = generateConsumerName();

        // Create consumer group (ignore error if already exists)
        createConsumerGroup()
                .onFailure().invoke(failure ->
                        Log.warnf(failure, "Consumer group creation failed "
                                + "(may already exist): %s", CONSUMER_GROUP))
                .subscribe().with(
                        success -> {
                            Log.infof("Consumer group initialized: %s", CONSUMER_GROUP);
                            startConsumer();
                        },
                        failure -> Log.errorf(failure,
                                "Failed to initialize consumer group: %s", CONSUMER_GROUP)
                );
    }

    /**
     * Creates the consumer group for the export jobs stream.
     * <p>
     * Uses MKSTREAM option to create stream if it doesn't exist.
     * </p>
     *
     * @return Uni completing when group is created
     */
    private Uni<Void> createConsumerGroup() {
        return streamCommands.xgroupCreate(
                        EXPORT_JOBS_STREAM,
                        CONSUMER_GROUP,
                        "0",  // Start from beginning
                        new XGroupCreateArgs().mkstream()  // Create stream if not exists
                )
                .replaceWithVoid()
                .onFailure().invoke(failure -> {
                    // Check if error is "BUSYGROUP" (group already exists) - this is OK
                    if (failure.getMessage() != null
                            && failure.getMessage().contains("BUSYGROUP")) {
                        Log.debugf("Consumer group already exists: %s", CONSUMER_GROUP);
                    } else {
                        Log.errorf(failure, "Failed to create consumer group: %s",
                                CONSUMER_GROUP);
                    }
                });
    }

    /**
     * Starts the consumer loop to process jobs from Redis Stream.
     */
    private void startConsumer() {
        Log.infof("Starting export job consumer: %s (consumer group: %s)",
                consumerName, CONSUMER_GROUP);

        running = true;

        // Start consuming messages in a loop
        consumeMessages();
    }

    /**
     * Consumes messages from Redis Stream in a continuous loop.
     * <p>
     * Uses XREADGROUP to read messages with consumer group for reliable processing.
     * Each message is acknowledged (XACK) after successful processing.
     * </p>
     */
    private void consumeMessages() {
        if (!running) {
            return;
        }

        // Read messages from stream (using ">" to read only new undelivered messages)
        streamCommands.xreadgroup(
                        CONSUMER_GROUP,
                        consumerName,
                        EXPORT_JOBS_STREAM,
                        ">",
                        new XReadGroupArgs()
                                .count(BATCH_SIZE)
                                .block(BLOCK_DURATION)
                )
                .onItem().transformToUni(messages -> {
                    if (messages.isEmpty()) {
                        Log.debug("No new export jobs, waiting...");
                        return Uni.createFrom().voidItem();
                    }

                    Log.infof("Received %d export job(s) from stream", messages.size());

                    // Process each message
                    return Uni.combine().all().unis(
                            messages.stream()
                                    .map(this::processMessage)
                                    .toList()
                    ).discardItems();
                })
                .subscribe().with(
                        success -> consumeMessages(),  // Continue consuming
                        failure -> {
                            Log.errorf(failure, "Error consuming messages from stream, retrying...");
                            // Wait 5 seconds before retrying
                            try {
                                Thread.sleep(5000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            consumeMessages();  // Retry
                        }
                );
    }

    /**
     * Processes a single export job message from the stream.
     *
     * @param message The stream message
     * @return Uni completing when message is processed
     */
    private Uni<Void> processMessage(final StreamMessage<String, String, String> message) {
        final String messageId = message.id();
        final Map<String, String> jobData = message.payload();

        Log.infof("Processing export job: messageId=%s, data=%s", messageId, jobData);

        final String jobId = jobData.get("jobId");
        final UUID sessionId = UUID.fromString(jobData.get("sessionId"));
        final String format = jobData.get("format");
        final UUID userId = UUID.fromString(jobData.get("userId"));

        // Process the job with retry logic
        return processExportJob(UUID.fromString(jobId), sessionId, format, userId)
                .onItemOrFailure().transformToUni((success, failure) -> {
                    if (failure != null) {
                        Log.errorf(failure, "Failed to process export job %s", jobId);
                    } else {
                        Log.infof("Successfully processed export job %s", jobId);
                    }

                    // Acknowledge message (mark as processed)
                    return streamCommands.xack(EXPORT_JOBS_STREAM, CONSUMER_GROUP, messageId)
                            .replaceWithVoid();
                });
    }

    /**
     * Processes an export job: generate file, upload to S3, update job status.
     * <p>
     * Includes retry logic with exponential backoff for transient failures.
     * </p>
     *
     * @param jobId The job UUID
     * @param sessionId The session UUID
     * @param format Export format (CSV or PDF)
     * @param userId The user UUID
     * @return Uni completing when job is processed
     */
    @Retry(maxRetries = 5, delay = 1000, jitter = 200)
    @ExponentialBackoff(factor = 2, maxDelay = 16000)
    public Uni<Void> processExportJob(final UUID jobId,
                                       final UUID sessionId,
                                       final String format,
                                       final UUID userId) {

        Log.infof("Processing export job %s for session %s (format: %s)",
                jobId, sessionId, format);

        // Find or create job record
        return ExportJob.findByJobId(jobId)
                .onItem().ifNull().switchTo(() -> createJobRecord(jobId, sessionId, format, userId))
                .onItem().transformToUni(job -> {
                    // Mark job as processing
                    return job.markAsProcessing()
                            .onItem().transformToUni(v ->
                                    // Fetch session data
                                    sessionHistoryService.getSessionById(sessionId)
                                            .onItem().ifNull().failWith(() ->
                                                    new IllegalArgumentException(
                                                            "Session not found: " + sessionId))
                                            .onItem().transformToUni(session ->
                                                    // Generate export file
                                                    generateExportFile(session, sessionId, format)
                                                            .onItem().transformToUni(fileContent ->
                                                                    // Upload to S3 and get signed URL
                                                                    s3Adapter.uploadFileAndGenerateUrl(
                                                                                    sessionId, jobId, format, fileContent)
                                                                            .onItem().transformToUni(downloadUrl ->
                                                                                    // Mark job as completed
                                                                                    job.markAsCompleted(downloadUrl)))));
                })
                .onFailure().recoverWithUni(failure -> {
                    // Handle failure: mark job as failed
                    Log.errorf(failure, "Export job %s failed", jobId);

                    return ExportJob.findByJobId(jobId)
                            .onItem().ifNotNull().transformToUni(job ->
                                    job.markAsFailed(failure.getMessage()));
                });
    }

    /**
     * Creates a new job record in the database.
     *
     * @param jobId The job UUID
     * @param sessionId The session UUID
     * @param format Export format
     * @param userId The user UUID
     * @return Uni containing the created job
     */
    private Uni<ExportJob> createJobRecord(final UUID jobId,
                                            final UUID sessionId,
                                            final String format,
                                            final UUID userId) {
        final ExportJob job = new ExportJob();
        job.jobId = jobId;
        job.sessionId = sessionId;
        job.format = format;
        job.status = JobStatus.PENDING;

        // Load user (simplified - in production, use repository)
        return User.<User>findById(userId)
                .onItem().ifNull().failWith(() ->
                        new IllegalArgumentException("User not found: " + userId))
                .onItem().transformToUni(user -> {
                    job.user = user;
                    job.createdAt = Instant.now();
                    return job.persistAndFlush().replaceWith(job);
                });
    }

    /**
     * Generates the export file content based on format.
     *
     * @param session The session history
     * @param sessionId The session UUID
     * @param format Export format (CSV or PDF)
     * @return Uni containing the file content as byte array
     */
    private Uni<byte[]> generateExportFile(final SessionHistory session,
                                            final UUID sessionId,
                                            final String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> csvExporter.generateCsvExport(session, sessionId);
            case "PDF" -> pdfExporter.generatePdfExport(session, sessionId);
            default -> Uni.createFrom().failure(
                    new IllegalArgumentException("Unsupported format: " + format));
        };
    }

    /**
     * Generates a unique consumer name for this worker instance.
     *
     * @return Consumer name (hostname-uuid)
     */
    private String generateConsumerName() {
        String hostname;
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }
        return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Health check method to verify consumer is running.
     * <p>
     * Called periodically by scheduler to log consumer status.
     * </p>
     */
    @Scheduled(every = "60s")
    void logConsumerStatus() {
        if (running) {
            Log.debugf("Export job consumer is running: %s", consumerName);
        } else {
            Log.warnf("Export job consumer is NOT running: %s", consumerName);
        }
    }

    /**
     * Gracefully stops the consumer on application shutdown.
     */
    void onStop() {
        Log.info("Stopping export job consumer...");
        running = false;

        if (consumerSubscription != null) {
            consumerSubscription.cancel();
        }

        Log.info("Export job consumer stopped");
    }
}
