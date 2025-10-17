package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating user profile information.
 * Matches OpenAPI UpdateUserRequest schema.
 * All fields are optional.
 */
public class UpdateProfileRequest {

    @JsonProperty("displayName")
    @Size(max = 100, message = "Display name cannot exceed 100 characters")
    public String displayName;

    @JsonProperty("avatarUrl")
    @Size(max = 500, message = "Avatar URL cannot exceed 500 characters")
    public String avatarUrl;
}
