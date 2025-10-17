package com.scrumpoker.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Data transfer object representing a pair of JWT access token and refresh token.
 * <p>
 * This record is returned when generating new tokens (during login or token refresh)
 * and contains both the short-lived access token and the long-lived refresh token.
 * </p>
 * <p>
 * <strong>Security Notes:</strong>
 * <ul>
 *   <li>Access tokens are short-lived (1 hour) and contain user claims for authorization</li>
 *   <li>Refresh tokens are long-lived (30 days) and are used to obtain new access tokens</li>
 *   <li>Refresh tokens are stored in Redis with TTL and can be rotated for enhanced security</li>
 *   <li>Both tokens should be transmitted over HTTPS only</li>
 * </ul>
 * </p>
 *
 * @param accessToken  The JWT access token (signed with RS256, contains user claims, 1-hour expiration)
 * @param refreshToken The refresh token (UUID stored in Redis with 30-day TTL)
 */
public record TokenPair(
    @JsonProperty("accessToken")
    @NotBlank(message = "Access token cannot be blank")
    String accessToken,

    @JsonProperty("refreshToken")
    @NotBlank(message = "Refresh token cannot be blank")
    String refreshToken
) {
    /**
     * Creates a new TokenPair with the given access and refresh tokens.
     * <p>
     * Both tokens must be non-null and non-blank. This constructor performs
     * compact constructor validation automatically via the record mechanism.
     * </p>
     *
     * @param accessToken  The JWT access token (must not be null or blank)
     * @param refreshToken The refresh token (must not be null or blank)
     * @throws IllegalArgumentException if either token is null or blank
     */
    public TokenPair {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token cannot be null or blank");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token cannot be null or blank");
        }
    }
}
