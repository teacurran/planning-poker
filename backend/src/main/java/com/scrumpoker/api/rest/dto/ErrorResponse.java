package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;

/**
 * Standard error response structure for REST API endpoints.
 * Matches OpenAPI ErrorResponse schema.
 */
public class ErrorResponse {

    @JsonProperty("error")
    public String error;

    @JsonProperty("message")
    public String message;

    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant timestamp;

    @JsonProperty("details")
    public Map<String, Object> details;

    public ErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now();
    }

    public ErrorResponse(String error, String message, Map<String, Object> details) {
        this.error = error;
        this.message = message;
        this.timestamp = Instant.now();
        this.details = details;
    }
}
