package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Request body for PUT /api/v1/rooms/{roomId}/config endpoint.
 * Matches OpenAPI UpdateRoomConfigRequest schema.
 */
public class UpdateRoomConfigRequest {

    @Size(max = 255, message = "Title must not exceed 255 characters")
    @JsonProperty("title")
    public String title;

    @JsonProperty("privacyMode")
    public String privacyMode;

    @JsonProperty("config")
    public RoomConfigDTO config;
}
