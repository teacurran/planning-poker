package com.scrumpoker.domain.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.repository.UserPreferenceRepository;
import com.scrumpoker.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.hibernate.reactive.panache.common.WithSession;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Domain service for user profile and preference management operations.
 * Handles user creation (from OAuth profile), profile updates, preference management,
 * and soft deletion with GDPR compliance.
 */
@ApplicationScoped
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    @Inject
    UserRepository userRepository;

    @Inject
    UserPreferenceRepository userPreferenceRepository;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Creates a new user from OAuth profile data and initializes default preferences.
     * This method is typically called during OAuth2 JIT (Just-In-Time) user provisioning.
     *
     * @param oauthProvider The OAuth provider (e.g., "google", "microsoft")
     * @param oauthSubject The OAuth subject identifier (unique user ID from provider)
     * @param email User's email address
     * @param displayName User's display name
     * @param avatarUrl URL to user's avatar image
     * @return Uni containing the created User entity
     * @throws IllegalArgumentException if validation fails
     */
    @WithTransaction
    public Uni<User> createUser(String oauthProvider, String oauthSubject, String email,
                                 String displayName, String avatarUrl) {
        // Validate inputs
        if (oauthProvider == null || oauthProvider.trim().isEmpty()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("OAuth provider cannot be null or empty")
            );
        }
        if (oauthSubject == null || oauthSubject.trim().isEmpty()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("OAuth subject cannot be null or empty")
            );
        }
        if (!isValidEmail(email)) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Invalid email format: " + email)
            );
        }
        if (!isValidDisplayName(displayName)) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Display name must be between 1 and " + MAX_DISPLAY_NAME_LENGTH + " characters"
                    )
            );
        }

        // Create User entity
        User user = new User();
        user.oauthProvider = oauthProvider;
        user.oauthSubject = oauthSubject;
        user.email = email;
        user.displayName = displayName;
        user.avatarUrl = avatarUrl;
        user.subscriptionTier = SubscriptionTier.FREE;
        // createdAt and updatedAt are set automatically by Hibernate

        // Persist user and create default preferences
        return userRepository.persist(user)
                .flatMap(savedUser -> createDefaultPreferences(savedUser)
                        .replaceWith(savedUser)
                );
    }

    /**
     * Creates default UserPreference record for a newly created user.
     */
    private Uni<UserPreference> createDefaultPreferences(User user) {
        UserPreference preference = new UserPreference();
        preference.userId = user.userId;
        preference.user = user;
        preference.defaultDeckType = "fibonacci";
        // theme defaults to "light" at database level

        // Set default JSONB configurations
        try {
            UserPreferenceConfig defaultConfig = UserPreferenceConfig.defaultConfig();
            preference.defaultRoomConfig = serializeConfig(defaultConfig);
            preference.notificationSettings = serializeConfig(defaultConfig);
        } catch (RuntimeException e) {
            // Fallback to empty JSON objects if serialization fails
            preference.defaultRoomConfig = "{}";
            preference.notificationSettings = "{}";
        }

        return userPreferenceRepository.persist(preference);
    }

    /**
     * Updates user profile information (display name and avatar URL).
     *
     * @param userId User ID
     * @param displayName New display name (optional)
     * @param avatarUrl New avatar URL (optional)
     * @return Uni containing the updated User entity
     * @throws UserNotFoundException if user not found or soft-deleted
     * @throws IllegalArgumentException if validation fails
     */
    @WithTransaction
    public Uni<User> updateProfile(UUID userId, String displayName, String avatarUrl) {
        return getUserById(userId)
                .flatMap(user -> {
                    // Validate display name if provided
                    if (displayName != null && !isValidDisplayName(displayName)) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException(
                                        "Display name must be between 1 and " + MAX_DISPLAY_NAME_LENGTH + " characters"
                                )
                        );
                    }

                    // Update fields if provided
                    if (displayName != null) {
                        user.displayName = displayName;
                    }
                    if (avatarUrl != null) {
                        user.avatarUrl = avatarUrl;
                    }

                    return userRepository.persist(user);
                });
    }

    /**
     * Retrieves a user by ID. Returns only active (non-deleted) users.
     *
     * @param userId User ID
     * @return Uni containing the User entity
     * @throws UserNotFoundException if user not found or soft-deleted
     */
    @WithSession
    public Uni<User> getUserById(UUID userId) {
        return userRepository.findById(userId)
                .onItem().ifNull().failWith(() -> new UserNotFoundException(userId))
                .onItem().transform(user -> {
                    if (user.deletedAt != null) {
                        throw new UserNotFoundException(userId);
                    }
                    return user;
                });
    }

    /**
     * Finds a user by email address. Returns only active (non-deleted) users.
     *
     * @param email Email address to search for
     * @return Uni containing the User entity, or null if not found
     */
    @WithSession
    public Uni<User> findByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Uni.createFrom().nullItem();
        }
        return userRepository.findActiveByEmail(email);
    }

    /**
     * Finds or creates a user based on OAuth provider and subject.
     * This is the primary method for OAuth2 JIT provisioning flow.
     *
     * @param oauthProvider The OAuth provider
     * @param oauthSubject The OAuth subject identifier
     * @param email User's email
     * @param displayName User's display name
     * @param avatarUrl User's avatar URL
     * @return Uni containing the found or created User entity
     */
    @WithTransaction
    public Uni<User> findOrCreateUser(String oauthProvider, String oauthSubject,
                                       String email, String displayName, String avatarUrl) {
        return userRepository.findByOAuthProviderAndSubject(oauthProvider, oauthSubject)
                .onItem().ifNotNull().transform(user -> {
                    // User exists, update profile if changed
                    boolean updated = false;
                    if (email != null && !email.equals(user.email)) {
                        user.email = email;
                        updated = true;
                    }
                    if (displayName != null && !displayName.equals(user.displayName)) {
                        user.displayName = displayName;
                        updated = true;
                    }
                    if (avatarUrl != null && !avatarUrl.equals(user.avatarUrl)) {
                        user.avatarUrl = avatarUrl;
                        updated = true;
                    }
                    return user;
                })
                .onItem().ifNull().switchTo(
                        // User doesn't exist, create new one
                        () -> createUser(oauthProvider, oauthSubject, email, displayName, avatarUrl)
                );
    }

    /**
     * Updates user preferences including default room configuration and notification settings.
     *
     * @param userId User ID
     * @param config Configuration object containing preferences to update
     * @return Uni containing the updated UserPreference entity
     * @throws UserNotFoundException if user not found
     */
    @WithTransaction
    public Uni<UserPreference> updatePreferences(UUID userId, UserPreferenceConfig config) {
        if (config == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException("Configuration cannot be null")
            );
        }

        // Verify user exists
        return getUserById(userId)
                .flatMap(user -> userPreferenceRepository.findById(userId)
                        .onItem().ifNull().failWith(() -> new UserNotFoundException(userId))
                        .flatMap(preference -> {
                            try {
                                // Update JSONB fields
                                if (config.deckType != null) {
                                    preference.defaultDeckType = config.deckType;
                                }
                                preference.defaultRoomConfig = serializeConfig(config);
                                preference.notificationSettings = serializeConfig(config);

                                return userPreferenceRepository.persist(preference);
                            } catch (RuntimeException e) {
                                return Uni.createFrom().failure(
                                        new IllegalArgumentException("Failed to serialize preferences", e)
                                );
                            }
                        })
                );
    }

    /**
     * Soft deletes a user by setting the deletedAt timestamp.
     * This preserves data for GDPR compliance and audit trails.
     *
     * @param userId User ID to delete
     * @return Uni containing the soft-deleted User entity
     * @throws UserNotFoundException if user not found or already deleted
     */
    @WithTransaction
    public Uni<User> deleteUser(UUID userId) {
        return getUserById(userId)
                .flatMap(user -> {
                    if (user.deletedAt != null) {
                        return Uni.createFrom().failure(
                                new IllegalArgumentException("User is already deleted: " + userId)
                        );
                    }

                    user.deletedAt = Instant.now();
                    return userRepository.persist(user);
                });
    }

    // ===== Private Helper Methods =====

    /**
     * Validates email format using regex pattern.
     */
    private boolean isValidEmail(String email) {
        return email != null && !email.trim().isEmpty() && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates display name length constraints.
     */
    private boolean isValidDisplayName(String displayName) {
        return displayName != null &&
                !displayName.trim().isEmpty() &&
                displayName.length() <= MAX_DISPLAY_NAME_LENGTH;
    }

    /**
     * Serializes UserPreferenceConfig to JSON string for JSONB storage.
     */
    private String serializeConfig(UserPreferenceConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize user preference config", e);
        }
    }

    /**
     * Deserializes JSON string to UserPreferenceConfig object.
     */
    private UserPreferenceConfig deserializeConfig(String json) {
        try {
            if (json == null || json.trim().isEmpty() || json.equals("{}")) {
                return UserPreferenceConfig.empty();
            }
            return objectMapper.readValue(json, UserPreferenceConfig.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize user preference config", e);
        }
    }
}
