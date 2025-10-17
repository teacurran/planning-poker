package com.scrumpoker.repository;

import com.scrumpoker.domain.user.UserPreference;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

/**
 * Reactive Panache repository for UserPreference entity.
 * UserPreference has a 1:1 relationship with User, sharing the same primary key.
 */
@ApplicationScoped
public class UserPreferenceRepository implements PanacheRepositoryBase<UserPreference, UUID> {

    /**
     * Find user preferences by user ID.
     *
     * @param userId The user ID to search for
     * @return Uni containing the user preferences if found, or null if not found
     */
    public Uni<UserPreference> findByUserId(UUID userId) {
        return findById(userId);
    }

    /**
     * Find user preferences by theme setting.
     * Useful for analytics or bulk updates.
     *
     * @param theme The theme value (e.g., "light", "dark")
     * @return Uni containing the count of users with that theme
     */
    public Uni<Long> countByTheme(String theme) {
        return count("theme", theme);
    }
}
