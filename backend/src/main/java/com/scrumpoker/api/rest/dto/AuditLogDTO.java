package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for audit log entry.
 */
public class AuditLogDTO {

    @NotNull
    @JsonProperty("logId")
    public UUID logId;

    @JsonProperty("orgId")
    public UUID orgId;

    @JsonProperty("userId")
    public UUID userId;

    @NotNull
    @JsonProperty("action")
    public String action;

    @NotNull
    @JsonProperty("resourceType")
    public String resourceType;

    @JsonProperty("resourceId")
    public String resourceId;

    @JsonProperty("ipAddress")
    public String ipAddress;

    @JsonProperty("userAgent")
    public String userAgent;

    @NotNull
    @JsonProperty("timestamp")
    public Instant timestamp;
}
