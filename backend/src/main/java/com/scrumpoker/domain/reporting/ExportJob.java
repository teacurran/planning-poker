package com.scrumpoker.domain.reporting;

import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tracks asynchronous CSV/PDF export job status and results.
 * <p>
 * Export jobs are created when users request session report downloads.
 * Background workers consume jobs from Redis Stream, generate files,
 * upload to S3, and update job status with download URL.
 * </p>
 *
 * <p><strong>State Lifecycle:</strong></p>
 * <pre>
 *   PENDING: Job enqueued, waiting for worker
 *      ↓
 *   PROCESSING: Worker generating file
 *      ↓
 *   COMPLETED: File uploaded, download URL available
 *   FAILED: Generation failed, error message stored
 * </pre>
 *
 * <p><strong>Usage Pattern:</strong></p>
 * <pre>
 *   // Create new job
 *   ExportJob job = new ExportJob();
 *   job.jobId = UUID.randomUUID();
 *   job.sessionId = sessionId;
 *   job.user = user;
 *   job.format = "CSV";
 *   job.status = JobStatus.PENDING;
 *   job.persist();
 *
 *   // Worker updates status
 *   job.status = JobStatus.PROCESSING;
 *   job.processingStartedAt = Instant.now();
 *   // ... generate file, upload to S3 ...
 *   job.status = JobStatus.COMPLETED;
 *   job.downloadUrl = s3SignedUrl;
 *   job.completedAt = Instant.now();
 * </pre>
 *
 * @see JobStatus
 * @see com.scrumpoker.worker.ExportJobProcessor
 */
@Entity
@Table(name = "export_job")
public class ExportJob extends PanacheEntityBase {

    /**
     * Unique job identifier (matches jobId in Redis Stream message).
     */
    @Id
    @Column(name = "job_id")
    public UUID jobId;

    /**
     * Session UUID to export.
     * <p>
     * NOTE: No foreign key constraint because SessionHistory uses a composite key.
     * Worker must validate session exists before processing.
     * </p>
     */
    @NotNull
    @Column(name = "session_id", nullable = false)
    public UUID sessionId;

    /**
     * User who requested the export.
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_export_job_user"))
    public User user;

    /**
     * Export format: "CSV" or "PDF".
     */
    @NotNull
    @Size(max = 10)
    @Column(name = "format", nullable = false, length = 10)
    public String format;

    /**
     * Job processing status.
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "job_status_enum")
    public JobStatus status = JobStatus.PENDING;

    /**
     * S3 signed URL for file download (7-day expiration).
     * <p>
     * Set when status transitions to COMPLETED.
     * Null for PENDING, PROCESSING, and FAILED jobs.
     * </p>
     */
    @Column(name = "download_url", columnDefinition = "TEXT")
    public String downloadUrl;

    /**
     * Error message if job failed.
     * <p>
     * Set when status transitions to FAILED.
     * Contains exception message and stack trace for debugging.
     * </p>
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    public String errorMessage;

    /**
     * Job creation timestamp (set automatically on insert).
     */
    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    /**
     * Timestamp when worker started processing the job.
     * <p>
     * Set when status transitions from PENDING to PROCESSING.
     * Used for timeout detection (jobs stuck in PROCESSING for too long).
     * </p>
     */
    @Column(name = "processing_started_at")
    public Instant processingStartedAt;

    /**
     * Timestamp when job completed successfully.
     * <p>
     * Set when status transitions to COMPLETED.
     * </p>
     */
    @Column(name = "completed_at")
    public Instant completedAt;

    /**
     * Timestamp when job failed.
     * <p>
     * Set when status transitions to FAILED.
     * </p>
     */
    @Column(name = "failed_at")
    public Instant failedAt;

    // ========================================================================
    // Query Methods
    // ========================================================================

    /**
     * Finds an export job by job ID.
     *
     * @param jobId The job UUID
     * @return Uni containing the job or null if not found
     */
    public static Uni<ExportJob> findByJobId(final UUID jobId) {
        return find("jobId", jobId).firstResult();
    }

    /**
     * Finds all export jobs for a specific user, ordered by creation time descending.
     *
     * @param userId The user UUID
     * @return Uni containing list of user's export jobs
     */
    public static Uni<List<ExportJob>> findByUserId(final UUID userId) {
        return find("user.userId = ?1 order by createdAt desc", userId).list();
    }

    /**
     * Finds all export jobs for a specific session.
     *
     * @param sessionId The session UUID
     * @return Uni containing list of session export jobs
     */
    public static Uni<List<ExportJob>> findBySessionId(final UUID sessionId) {
        return find("sessionId = ?1 order by createdAt desc", sessionId).list();
    }

    /**
     * Finds all jobs with a specific status, ordered by creation time ascending.
     * <p>
     * Useful for finding PENDING jobs to process.
     * </p>
     *
     * @param status The job status to filter by
     * @return Uni containing list of jobs with the specified status
     */
    public static Uni<List<ExportJob>> findByStatus(final JobStatus status) {
        return find("status = ?1 order by createdAt asc", status).list();
    }

    /**
     * Marks the job as processing.
     * <p>
     * Updates status to PROCESSING and sets processingStartedAt timestamp.
     * </p>
     *
     * @return Uni completing when update is persisted
     */
    public Uni<Void> markAsProcessing() {
        this.status = JobStatus.PROCESSING;
        this.processingStartedAt = Instant.now();
        return persistAndFlush().replaceWithVoid();
    }

    /**
     * Marks the job as completed with download URL.
     * <p>
     * Updates status to COMPLETED, sets downloadUrl and completedAt timestamp.
     * </p>
     *
     * @param downloadUrl The S3 signed URL for file download
     * @return Uni completing when update is persisted
     */
    public Uni<Void> markAsCompleted(final String downloadUrl) {
        this.status = JobStatus.COMPLETED;
        this.downloadUrl = downloadUrl;
        this.completedAt = Instant.now();
        return persistAndFlush().replaceWithVoid();
    }

    /**
     * Marks the job as failed with error message.
     * <p>
     * Updates status to FAILED, sets errorMessage and failedAt timestamp.
     * </p>
     *
     * @param errorMessage The error description
     * @return Uni completing when update is persisted
     */
    public Uni<Void> markAsFailed(final String errorMessage) {
        this.status = JobStatus.FAILED;
        this.errorMessage = errorMessage;
        this.failedAt = Instant.now();
        return persistAndFlush().replaceWithVoid();
    }
}
