package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for refresh token endpoint.
 * Contains the refresh token to be validated and exchanged for new tokens.
 */
public class RefreshTokenRequest {

    /**
     * Refresh token to be validated and exchanged.
     */
    @JsonProperty("refreshToken")
    @NotBlank(message = "Refresh token is required")
    public String refreshToken;

    /**
     * Default constructor for Jackson deserialization.
     */
    public RefreshTokenRequest() {
    }

    /**
     * Constructor for testing.
     *
     * @param refreshToken The refresh token
     */
    public RefreshTokenRequest(final String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
