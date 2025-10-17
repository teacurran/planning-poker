package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/v1/rooms endpoint.
 * Matches OpenAPI CreateRoomRequest schema.
 */
public class CreateRoomRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must not exceed 255 characters")
    @JsonProperty("title")
    public String title;

    @JsonProperty("privacyMode")
    public String privacyMode; // Optional, defaults to PUBLIC

    @JsonProperty("config")
    public RoomConfigDTO config; // Optional
}
