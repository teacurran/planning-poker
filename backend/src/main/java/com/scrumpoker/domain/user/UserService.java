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

    /**
     * Email validation pattern following RFC 5322.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    /**
     * Maximum allowed length for user display name.
     */
    private static final int MAX_DISPLAY_NAME_LENGTH = 100;

    /**
     * Repository for user entity operations.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Repository for user preference entity operations.
     */
    @Inject
    private UserPreferenceRepository userPreferenceRepository;

    /**
     * Jackson object mapper for JSON serialization/deserialization.
     */
    @Inject
    private ObjectMapper objectMapper;

    /**
     * Creates a new user from OAuth profile data and initializes
     * default preferences.
     * This method is typically called during OAuth2 JIT
     * (Just-In-Time) user provisioning.
     *
     * @param oauthProvider The OAuth provider
     *        (e.g., "google", "microsoft")
     * @param oauthSubject The OAuth subject identifier
     *        (unique user ID from provider)
     * @param email User's email address
     * @param displayName User's display name
     * @param avatarUrl URL to user's avatar image
     * @return Uni containing the created User entity
     * @throws IllegalArgumentException if validation fails
     */
    @WithTransaction
    public Uni<User> createUser(final String oauthProvider,
                                 final String oauthSubject,
                                 final String email,
                                 final String displayName,
                                 final String avatarUrl) {
        // Validate inputs
        if (oauthProvider == null || oauthProvider.trim().isEmpty()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "OAuth provider cannot be null or empty")
            );
        }
        if (oauthSubject == null || oauthSubject.trim().isEmpty()) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "OAuth subject cannot be null or empty")
            );
        }
        if (!isValidEmail(email)) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Invalid email format: " + email)
            );
        }
        if (!isValidDisplayName(displayName)) {
            final String msg = "Display name must be between 1 and "
                    + MAX_DISPLAY_NAME_LENGTH + " characters";
            return Uni.createFrom().failure(
                    new IllegalArgumentException(msg)
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
     *
     * @param user The user for whom preferences are being created
     * @return Uni containing the created UserPreference entity
     */
    private Uni<UserPreference> createDefaultPreferences(
            final User user) {
        UserPreference preference = new UserPreference();
        preference.userId = user.userId;
        preference.user = user;
        preference.defaultDeckType = "fibonacci";
        // theme defaults to "light" at database level

        // Manually set timestamps
        // (Hibernate Reactive doesn't reliably support @CreationTimestamp)
        Instant now = Instant.now();
        preference.createdAt = now;
        preference.updatedAt = now;

        // Set default JSONB configurations
        try {
            final UserPreferenceConfig defaultConfig =
                    UserPreferenceConfig.defaultConfig();
            preference.defaultRoomConfig = serializeConfig(defaultConfig);
            preference.notificationSettings =
                    serializeConfig(defaultConfig);
        } catch (RuntimeException e) {
            // Fallback to empty JSON objects if serialization fails
            preference.defaultRoomConfig = "{}";
            preference.notificationSettings = "{}";
        }

        return userPreferenceRepository.persist(preference);
    }

    /**
     * Updates user profile information
     * (display name and avatar URL).
     *
     * @param userId User ID
     * @param displayName New display name (optional)
     * @param avatarUrl New avatar URL (optional)
     * @return Uni containing the updated User entity
     * @throws UserNotFoundException if user not found or soft-deleted
     * @throws IllegalArgumentException if validation fails
     */
    @WithTransaction
    public Uni<User> updateProfile(final UUID userId,
                                     final String displayName,
                                     final String avatarUrl) {
        return getUserById(userId)
                .flatMap(user -> {
                    // Validate display name if provided
                    if (displayName != null
                            && !isValidDisplayName(displayName)) {
                        final String msg = "Display name must be between 1 "
                                + "and " + MAX_DISPLAY_NAME_LENGTH
                                + " characters";
                        return Uni.createFrom().failure(
                                new IllegalArgumentException(msg)
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
    public Uni<User> getUserById(final UUID userId) {
        return userRepository.findById(userId)
                .onItem().ifNull().failWith(
                        () -> new UserNotFoundException(userId))
                .onItem().transform(user -> {
                    if (user.deletedAt != null) {
                        throw new UserNotFoundException(userId);
                    }
                    return user;
                });
    }

    /**
     * Finds a user by email address.
     * Returns only active (non-deleted) users.
     *
     * @param email Email address to search for
     * @return Uni containing the User entity, or null if not found
     */
    @WithSession
    public Uni<User> findByEmail(final String email) {
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
    public Uni<User> findOrCreateUser(final String oauthProvider,
                                       final String oauthSubject,
                                       final String email,
                                       final String displayName,
                                       final String avatarUrl) {
        return userRepository.findByOAuthProviderAndSubject(
                oauthProvider, oauthSubject)
                .onItem().ifNotNull().transform(user -> {
                    // User exists, update profile if changed
                    if (email != null && !email.equals(user.email)) {
                        user.email = email;
                    }
                    if (displayName != null
                            && !displayName.equals(user.displayName)) {
                        user.displayName = displayName;
                    }
                    if (avatarUrl != null
                            && !avatarUrl.equals(user.avatarUrl)) {
                        user.avatarUrl = avatarUrl;
                    }
                    return user;
                })
                .onItem().ifNull().switchTo(
                        // User doesn't exist, create new one
                        () -> createUser(oauthProvider, oauthSubject,
                                email, displayName, avatarUrl)
                );
    }

    /**
     * Updates user preferences including default room configuration
     * and notification settings.
     *
     * @param userId User ID
     * @param config Configuration object containing preferences to update
     * @return Uni containing the updated UserPreference entity
     * @throws UserNotFoundException if user not found
     */
    @WithTransaction
    public Uni<UserPreference> updatePreferences(
            final UUID userId, final UserPreferenceConfig config) {
        if (config == null) {
            return Uni.createFrom().failure(
                    new IllegalArgumentException(
                            "Configuration cannot be null")
            );
        }

        // Verify user exists
        return getUserById(userId)
                .flatMap(user -> userPreferenceRepository.findById(userId)
                        .onItem().ifNull().failWith(
                                () -> new UserNotFoundException(userId))
                        .flatMap(preference -> {
                            try {
                                // Update JSONB fields
                                if (config.deckType != null) {
                                    preference.defaultDeckType =
                                            config.deckType;
                                }
                                preference.defaultRoomConfig =
                                        serializeConfig(config);
                                preference.notificationSettings =
                                        serializeConfig(config);

                                return userPreferenceRepository
                                        .persist(preference);
                            } catch (RuntimeException e) {
                                final String msg =
                                        "Failed to serialize preferences";
                                return Uni.createFrom().failure(
                                        new IllegalArgumentException(msg, e)
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
    public Uni<User> deleteUser(final UUID userId) {
        return getUserById(userId)
                .flatMap(user -> {
                    if (user.deletedAt != null) {
                        final String msg = "User is already deleted: "
                                + userId;
                        return Uni.createFrom().failure(
                                new IllegalArgumentException(msg)
                        );
                    }

                    user.deletedAt = Instant.now();
                    return userRepository.persist(user);
                });
    }

    // ===== Private Helper Methods =====

    /**
     * Validates email format using regex pattern.
     *
     * @param email The email address to validate
     * @return true if email is valid, false otherwise
     */
    private boolean isValidEmail(final String email) {
        return email != null && !email.trim().isEmpty()
                && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * Validates display name length constraints.
     *
     * @param displayName The display name to validate
     * @return true if display name is valid, false otherwise
     */
    private boolean isValidDisplayName(final String displayName) {
        return displayName != null
                && !displayName.trim().isEmpty()
                && displayName.length() <= MAX_DISPLAY_NAME_LENGTH;
    }

    /**
     * Serializes UserPreferenceConfig to JSON string for JSONB storage.
     *
     * @param config The configuration object to serialize
     * @return JSON string representation
     * @throws RuntimeException if serialization fails
     */
    private String serializeConfig(final UserPreferenceConfig config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            final String msg = "Failed to serialize user preference config";
            throw new RuntimeException(msg, e);
        }
    }

    /**
     * Deserializes JSON string to UserPreferenceConfig object.
     *
     * @param json The JSON string to deserialize
     * @return UserPreferenceConfig object
     * @throws RuntimeException if deserialization fails
     */
    private UserPreferenceConfig deserializeConfig(final String json) {
        try {
            if (json == null || json.trim().isEmpty()
                    || json.equals("{}")) {
                return UserPreferenceConfig.empty();
            }
            return objectMapper.readValue(json,
                    UserPreferenceConfig.class);
        } catch (JsonProcessingException e) {
            final String msg =
                    "Failed to deserialize user preference config";
            throw new RuntimeException(msg, e);
        }
    }
}
