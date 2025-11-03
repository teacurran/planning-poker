package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for job status polling.
 * Contains job state, download URL (when complete), and error message (when failed).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class JobStatusResponse {

    /**
     * Job identifier.
     */
    @JsonProperty("job_id")
    public UUID jobId;

    /**
     * Job status: PENDING, PROCESSING, COMPLETED, or FAILED.
     */
    @JsonProperty("status")
    public String status;

    /**
     * Download URL for completed export (7-day expiration).
     * Only present when status is COMPLETED.
     */
    @JsonProperty("download_url")
    public String downloadUrl;

    /**
     * Error message if job failed.
     * Only present when status is FAILED.
     */
    @JsonProperty("error_message")
    public String errorMessage;

    /**
     * Job creation timestamp (UTC).
     */
    @JsonProperty("created_at")
    public Instant createdAt;

    /**
     * Job completion timestamp (UTC).
     * Only present when status is COMPLETED or FAILED.
     */
    @JsonProperty("completed_at")
    public Instant completedAt;

    /**
     * Default constructor for Jackson deserialization.
     */
    public JobStatusResponse() {
    }

    /**
     * Constructs a JobStatusResponse with all fields.
     *
     * @param jobId Job UUID
     * @param status Job status
     * @param downloadUrl Download URL (nullable)
     * @param errorMessage Error message (nullable)
     * @param createdAt Creation timestamp
     * @param completedAt Completion timestamp (nullable)
     */
    public JobStatusResponse(final UUID jobId,
                             final String status,
                             final String downloadUrl,
                             final String errorMessage,
                             final Instant createdAt,
                             final Instant completedAt) {
        this.jobId = jobId;
        this.status = status;
        this.downloadUrl = downloadUrl;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }
}
