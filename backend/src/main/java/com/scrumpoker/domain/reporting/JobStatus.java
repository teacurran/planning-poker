package com.scrumpoker.domain.reporting;

/**
 * Export job processing status lifecycle matching database job_status_enum.
 * <p>
 * Represents the state transitions for asynchronous CSV/PDF export jobs:
 * <ul>
 *   <li><strong>PENDING</strong>: Job enqueued, waiting for worker to pick up</li>
 *   <li><strong>PROCESSING</strong>: Worker actively generating export file</li>
 *   <li><strong>COMPLETED</strong>: Export successfully generated, file available for download</li>
 *   <li><strong>FAILED</strong>: Export generation failed, error message stored</li>
 * </ul>
 * </p>
 *
 * <p><strong>State Transitions:</strong></p>
 * <pre>
 *   PENDING → PROCESSING → COMPLETED
 *                       ↘
 *                         FAILED
 * </pre>
 */
public enum JobStatus {
    /**
     * Job has been enqueued to Redis Stream but not yet picked up by a worker.
     */
    PENDING,

    /**
     * Worker is actively processing the job (querying data, generating file).
     */
    PROCESSING,

    /**
     * Export file successfully generated and uploaded to S3.
     * Download URL available in ExportJob.downloadUrl.
     */
    COMPLETED,

    /**
     * Export generation failed due to error.
     * Error details stored in ExportJob.errorMessage.
     */
    FAILED
}
