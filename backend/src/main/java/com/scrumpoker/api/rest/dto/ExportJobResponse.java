package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * Response DTO for export job creation.
 * Contains the job ID for polling status.
 */
public class ExportJobResponse {

    /**
     * Unique job identifier for polling status.
     */
    @JsonProperty("job_id")
    public UUID jobId;

    /**
     * Default constructor for Jackson deserialization.
     */
    public ExportJobResponse() {
    }

    /**
     * Constructs an ExportJobResponse with job ID.
     *
     * @param jobId Job UUID
     */
    public ExportJobResponse(final UUID jobId) {
        this.jobId = jobId;
    }
}
