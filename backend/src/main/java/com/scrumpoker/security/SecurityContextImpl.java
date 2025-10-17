package com.scrumpoker.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Helper service for accessing the current user's security context and JWT claims.
 * <p>
 * This service provides convenient methods for controllers and services to access
 * the authenticated user's information from the Quarkus security context. It wraps
 * the {@link SecurityIdentity} API and provides type-safe access to JWT claims.
 * </p>
 * <p>
 * <strong>Usage in Controllers:</strong>
 * <pre>
 * {@code @Inject}
 * SecurityContextImpl securityContext;
 *
 * public Uni&lt;Response&gt; getUserProfile(UUID userId) {
 *     UUID currentUserId = securityContext.getCurrentUserId();
 *     if (!currentUserId.equals(userId)) {
 *         return Uni.createFrom().item(Response.status(403).build());
 *     }
 *     // ...
 * }
 * </pre>
 * </p>
 * <p>
 * <strong>Available Information:</strong>
 * <ul>
 *   <li>User ID (UUID) - from JWT subject claim</li>
 *   <li>Email - from JWT custom claim</li>
 *   <li>Roles - from JWT custom claim (e.g., "USER", "PRO_USER", "ORG_MEMBER")</li>
 *   <li>Subscription Tier - from JWT custom claim (e.g., "FREE", "PRO", "ENTERPRISE")</li>
 *   <li>Full JwtClaims object - for advanced use cases</li>
 * </ul>
 * </p>
 * <p>
 * <strong>Security Notes:</strong>
 * <ul>
 *   <li>All methods assume the request has been authenticated by {@link JwtAuthenticationFilter}</li>
 *   <li>Calling these methods on public endpoints will return null or throw exceptions</li>
 *   <li>Always use {@code @RolesAllowed} annotations on endpoints before calling these methods</li>
 * </ul>
 * </p>
 *
 * @see JwtAuthenticationFilter
 * @see JwtClaims
 * @see SecurityIdentity
 */
@ApplicationScoped
public class SecurityContextImpl {

    /**
     * Key used to retrieve JwtClaims from SecurityIdentity attributes.
     * Must match the key used in JwtAuthenticationFilter.
     */
    private static final String JWT_CLAIMS_ATTRIBUTE = "jwt.claims";

    @Inject
    SecurityIdentity securityIdentity;

    /**
     * Gets the current authenticated user's ID.
     * <p>
     * This is the UUID from the JWT's subject (sub) claim.
     * </p>
     *
     * @return The current user's ID, or null if not authenticated
     * @throws IllegalStateException if JWT claims are not available in security context
     */
    public UUID getCurrentUserId() {
        JwtClaims claims = getCurrentClaims();
        return claims != null ? claims.userId() : null;
    }

    /**
     * Gets the current authenticated user's email address.
     * <p>
     * This is the email from the JWT's custom email claim.
     * </p>
     *
     * @return The current user's email, or null if not authenticated
     * @throws IllegalStateException if JWT claims are not available in security context
     */
    public String getCurrentUserEmail() {
        JwtClaims claims = getCurrentClaims();
        return claims != null ? claims.email() : null;
    }

    /**
     * Gets the current authenticated user's subscription tier.
     * <p>
     * This is the tier from the JWT's custom tier claim (e.g., "FREE", "PRO", "ENTERPRISE").
     * </p>
     *
     * @return The current user's subscription tier, or null if not authenticated
     * @throws IllegalStateException if JWT claims are not available in security context
     */
    public String getCurrentUserTier() {
        JwtClaims claims = getCurrentClaims();
        return claims != null ? claims.tier() : null;
    }

    /**
     * Gets the full JWT claims object for the current authenticated user.
     * <p>
     * This provides access to all JWT claims including userId, email, roles, and tier.
     * Use this method when you need access to multiple claim values or want to use
     * the convenience methods on {@link JwtClaims} (e.g., {@code hasRole()}).
     * </p>
     *
     * @return The current user's JWT claims, or null if not authenticated
     * @throws IllegalStateException if security identity is available but claims are missing
     */
    public JwtClaims getCurrentClaims() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return null;
        }

        Object claimsObj = securityIdentity.getAttribute(JWT_CLAIMS_ATTRIBUTE);
        if (claimsObj == null) {
            throw new IllegalStateException(
                "JWT claims not found in security context. " +
                "This endpoint may not have been properly authenticated.");
        }

        if (!(claimsObj instanceof JwtClaims)) {
            throw new IllegalStateException(
                "Invalid JWT claims type in security context: " + claimsObj.getClass());
        }

        return (JwtClaims) claimsObj;
    }

    /**
     * Checks if the current user has a specific role.
     * <p>
     * This is a convenience method that delegates to the Quarkus SecurityIdentity.
     * </p>
     *
     * @param role The role name to check (e.g., "PRO_USER", "ORG_ADMIN")
     * @return true if the user has the role, false otherwise (including if not authenticated)
     */
    public boolean hasRole(String role) {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return false;
        }
        return securityIdentity.hasRole(role);
    }

    /**
     * Checks if the current user is authenticated.
     * <p>
     * Returns true if a valid JWT was provided and validated.
     * </p>
     *
     * @return true if authenticated, false if anonymous
     */
    public boolean isAuthenticated() {
        return securityIdentity != null && !securityIdentity.isAnonymous();
    }

    /**
     * Validates that the current user is accessing their own resource.
     * <p>
     * This is a common authorization check for endpoints like GET/PUT /users/{userId}
     * where users should only access their own data.
     * </p>
     *
     * @param resourceUserId The user ID from the request path parameter
     * @return true if the current user owns the resource, false otherwise
     * @throws IllegalStateException if not authenticated
     */
    public boolean isCurrentUser(UUID resourceUserId) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            throw new IllegalStateException("User not authenticated");
        }
        return currentUserId.equals(resourceUserId);
    }
}
