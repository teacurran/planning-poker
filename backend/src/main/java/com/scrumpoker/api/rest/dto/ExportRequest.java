package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Request DTO for creating an export job.
 * Specifies the session to export and the desired format.
 */
public class ExportRequest {

    /**
     * Session ID to export.
     */
    @NotNull(message = "sessionId is required")
    @JsonProperty("session_id")
    public UUID sessionId;

    /**
     * Export format: "CSV" or "PDF".
     */
    @NotNull(message = "format is required")
    @Pattern(regexp = "^(CSV|PDF)$", message = "format must be 'CSV' or 'PDF'")
    @JsonProperty("format")
    public String format;

    /**
     * Default constructor for Jackson deserialization.
     */
    public ExportRequest() {
    }

    /**
     * Constructs an ExportRequest with all fields.
     *
     * @param sessionId Session UUID
     * @param format Export format
     */
    public ExportRequest(final UUID sessionId, final String format) {
        this.sessionId = sessionId;
        this.format = format;
    }
}
