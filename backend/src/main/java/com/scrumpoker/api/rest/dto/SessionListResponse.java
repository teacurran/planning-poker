package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scrumpoker.domain.reporting.SessionSummaryDTO;

import java.util.List;

/**
 * Response DTO for paginated session list endpoint.
 * Contains session summaries along with pagination metadata.
 */
public class SessionListResponse {

    /**
     * List of session summaries for the current page.
     */
    @JsonProperty("sessions")
    public List<SessionSummaryDTO> sessions;

    /**
     * Current page number (0-indexed).
     */
    @JsonProperty("page")
    public int page;

    /**
     * Page size (number of items per page).
     */
    @JsonProperty("size")
    public int size;

    /**
     * Total number of sessions matching the query.
     */
    @JsonProperty("total")
    public int total;

    /**
     * Whether there are more pages available.
     */
    @JsonProperty("has_next")
    public boolean hasNext;

    /**
     * Default constructor for Jackson deserialization.
     */
    public SessionListResponse() {
    }

    /**
     * Constructs a SessionListResponse with all fields.
     *
     * @param sessions List of session summaries
     * @param page Current page number
     * @param size Page size
     * @param total Total count
     * @param hasNext Whether more pages exist
     */
    public SessionListResponse(final List<SessionSummaryDTO> sessions,
                               final int page,
                               final int size,
                               final int total,
                               final boolean hasNext) {
        this.sessions = sessions;
        this.page = page;
        this.size = size;
        this.total = total;
        this.hasNext = hasNext;
    }
}
