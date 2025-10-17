package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response body for GET /api/v1/users/{userId}/rooms endpoint.
 * Provides paginated list of rooms owned by a user.
 */
public class RoomListResponse {

    @JsonProperty("rooms")
    public List<RoomDTO> rooms;

    @JsonProperty("page")
    public Integer page;

    @JsonProperty("size")
    public Integer size;

    @JsonProperty("totalElements")
    public Long totalElements;

    @JsonProperty("totalPages")
    public Integer totalPages;
}
