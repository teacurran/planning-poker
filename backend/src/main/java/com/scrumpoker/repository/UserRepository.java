package com.scrumpoker.repository;

import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * Reactive Panache repository for User entity.
 * Provides custom finder methods for OAuth lookups and email-based queries.
 */
@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, UUID> {

    /**
     * Find user by email address.
     *
     * @param email The email address to search for
     * @return Uni containing the user if found, or null if not found
     */
    public Uni<User> findByEmail(String email) {
        return find("email", email).firstResult();
    }

    /**
     * Find user by OAuth provider and subject identifier.
     * Used for OAuth authentication flow.
     *
     * @param provider OAuth provider (e.g., "google", "github")
     * @param subject OAuth subject identifier
     * @return Uni containing the user if found, or null if not found
     */
    public Uni<User> findByOAuthProviderAndSubject(String provider, String subject) {
        return find("oauthProvider = ?1 and oauthSubject = ?2", provider, subject).firstResult();
    }

    /**
     * Find user by email excluding soft-deleted users.
     *
     * @param email The email address to search for
     * @return Uni containing the active user if found, or null if not found
     */
    public Uni<User> findActiveByEmail(String email) {
        return find("email = ?1 and deletedAt is null", email).firstResult();
    }

    /**
     * Count total active users (excluding soft-deleted).
     *
     * @return Uni containing the count of active users
     */
    public Uni<Long> countActive() {
        return count("deletedAt is null");
    }
}
