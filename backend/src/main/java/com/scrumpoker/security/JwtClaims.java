package com.scrumpoker.security;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Data transfer object representing the claims extracted from a validated JWT access token.
 * <p>
 * This record encapsulates the standard and custom claims that are included in JWT tokens
 * for authorization and user identification purposes. Claims are extracted during token
 * validation and used throughout the application for access control decisions.
 * </p>
 * <p>
 * <strong>Included Claims:</strong>
 * <ul>
 *   <li><strong>userId (sub):</strong> Subject - unique user identifier (UUID)</li>
 *   <li><strong>email:</strong> User's email address for display and audit purposes</li>
 *   <li><strong>roles:</strong> List of role names for RBAC (e.g., USER, PRO_USER, ORG_ADMIN)</li>
 *   <li><strong>tier:</strong> Subscription tier (FREE, PRO, PRO_PLUS, ENTERPRISE) for feature gating</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Usage Example:</strong>
 * <pre>
 * JwtClaims claims = jwtTokenService.validateAccessToken(token);
 * if (claims.roles().contains("PRO_USER")) {
 *     // Grant access to pro-tier features
 * }
 * </pre>
 * </p>
 *
 * @param userId The unique user identifier (extracted from 'sub' claim)
 * @param email  The user's email address (custom claim)
 * @param roles  The list of role names assigned to the user (custom claim for RBAC)
 * @param tier   The subscription tier name (FREE, PRO, PRO_PLUS, ENTERPRISE)
 */
public record JwtClaims(
    @JsonProperty("userId")
    @NotNull(message = "User ID cannot be null")
    UUID userId,

    @JsonProperty("email")
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email must be valid")
    String email,

    @JsonProperty("roles")
    @NotEmpty(message = "Roles list cannot be empty")
    List<String> roles,

    @JsonProperty("tier")
    @NotBlank(message = "Subscription tier cannot be blank")
    String tier
) {
    /**
     * Creates a new JwtClaims instance with the given claim values.
     * <p>
     * All fields are required and validated. Roles list is copied to ensure immutability.
     * </p>
     *
     * @param userId The unique user identifier (must not be null)
     * @param email  The user's email address (must not be null or blank)
     * @param roles  The list of role names (must not be null or empty)
     * @param tier   The subscription tier (must not be null or blank)
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public JwtClaims {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or blank");
        }
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Roles list cannot be null or empty");
        }
        if (tier == null || tier.isBlank()) {
            throw new IllegalArgumentException("Subscription tier cannot be null or blank");
        }
        // Create defensive copy of roles list to ensure immutability
        roles = List.copyOf(roles);
    }

    /**
     * Checks if the user has a specific role.
     *
     * @param role The role name to check (e.g., "PRO_USER", "ORG_ADMIN")
     * @return true if the user has the specified role, false otherwise
     */
    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    /**
     * Checks if the user has any of the specified roles.
     *
     * @param rolesToCheck The roles to check against
     * @return true if the user has at least one of the specified roles, false otherwise
     */
    public boolean hasAnyRole(String... rolesToCheck) {
        for (String role : rolesToCheck) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
