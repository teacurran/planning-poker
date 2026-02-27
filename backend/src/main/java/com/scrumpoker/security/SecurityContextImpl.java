package com.scrumpoker.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Helper service for accessing the current user's security context.
 * <p>
 * This service provides convenient methods for controllers and
 * services to access the authenticated user's information from the
 * Quarkus security context. It wraps the {@link SecurityIdentity}
 * API and provides type-safe access to user attributes.
 * </p>
 *
 * @see SecurityIdentity
 */
@ApplicationScoped
public class SecurityContextImpl {

    /**
     * The current security identity, injected by Quarkus.
     * Contains user principal, roles, and attributes.
     */
    @Inject
    private SecurityIdentity securityIdentity;

    /**
     * Gets the current authenticated user's ID.
     * <p>
     * Parses the principal name from the SecurityIdentity as a UUID.
     * </p>
     *
     * @return The current user's ID, or null if not authenticated
     */
    public UUID getCurrentUserId() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return null;
        }
        String principal = securityIdentity.getPrincipal().getName();
        return UUID.fromString(principal);
    }

    /**
     * Gets the current authenticated user's email address.
     * <p>
     * Retrieves the email from the SecurityIdentity attributes.
     * </p>
     *
     * @return The current user's email, or null if not authenticated
     */
    public String getCurrentUserEmail() {
        if (securityIdentity == null || securityIdentity.isAnonymous()) {
            return null;
        }
        Object email = securityIdentity.getAttribute("email");
        return email != null ? email.toString() : null;
    }

    /**
     * Checks if the current user has a specific role.
     *
     * @param role The role name to check (e.g., "PRO_USER",
     *             "ORG_ADMIN")
     * @return true if the user has the role, false otherwise
     *         (including if not authenticated)
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
     * Returns true if a valid credential was provided and validated.
     * </p>
     *
     * @return true if authenticated, false if anonymous
     */
    public boolean isAuthenticated() {
        return securityIdentity != null && !securityIdentity.isAnonymous();
    }

    /**
     * Validates that the current user is accessing their own resource.
     *
     * @param resourceUserId The user ID from the request path
     *                       parameter
     * @return true if the current user owns the resource, false
     *         otherwise
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
