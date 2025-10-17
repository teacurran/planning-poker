package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Response DTO for authentication endpoints.
 * Contains JWT access token, refresh token, expiration info, and user profile.
 * Matches OpenAPI TokenResponse schema definition.
 */
public class TokenResponse {

    /**
     * JWT access token (15-minute expiry).
     */
    @JsonProperty("accessToken")
    @NotBlank(message = "Access token cannot be blank")
    public String accessToken;

    /**
     * Refresh token (30-day expiry, single-use).
     */
    @JsonProperty("refreshToken")
    @NotBlank(message = "Refresh token cannot be blank")
    public String refreshToken;

    /**
     * Access token TTL in seconds (typically 900 seconds = 15 minutes).
     */
    @JsonProperty("expiresIn")
    @Positive(message = "Expires in must be positive")
    public int expiresIn;

    /**
     * Token type (always "Bearer" for JWT tokens).
     */
    @JsonProperty("tokenType")
    @NotBlank(message = "Token type cannot be blank")
    public String tokenType;

    /**
     * User profile information.
     */
    @JsonProperty("user")
    @NotNull(message = "User cannot be null")
    public UserDTO user;

    /**
     * Default constructor for Jackson deserialization.
     */
    public TokenResponse() {
        this.tokenType = "Bearer";
    }

    /**
     * Full constructor for creating response instances.
     *
     * @param accessToken  JWT access token
     * @param refreshToken Refresh token
     * @param expiresIn    Access token TTL in seconds
     * @param user         User profile DTO
     */
    public TokenResponse(final String accessToken,
                         final String refreshToken,
                         final int expiresIn,
                         final UserDTO user) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
        this.tokenType = "Bearer";
        this.user = user;
    }
}
