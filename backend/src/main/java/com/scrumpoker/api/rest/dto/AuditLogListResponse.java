package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Paginated response for audit log entries.
 */
public class AuditLogListResponse {

    @NotNull
    @JsonProperty("logs")
    public List<AuditLogDTO> logs;

    @NotNull
    @JsonProperty("page")
    public Integer page;

    @NotNull
    @JsonProperty("size")
    public Integer size;

    @NotNull
    @JsonProperty("totalElements")
    public Long totalElements;

    @NotNull
    @JsonProperty("totalPages")
    public Integer totalPages;
}
