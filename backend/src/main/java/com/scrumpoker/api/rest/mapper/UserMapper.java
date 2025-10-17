package com.scrumpoker.api.rest.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.domain.user.UserPreference;
import com.scrumpoker.domain.user.UserPreferenceConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Mapper for converting between User/UserPreference entities and DTOs.
 * Handles JSONB serialization/deserialization for preference configuration.
 */
@ApplicationScoped
public class UserMapper {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Converts a User entity to UserDTO for REST API responses.
     *
     * @param user The user entity
     * @return UserDTO with all fields mapped
     */
    public UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }

        UserDTO dto = new UserDTO();
        dto.userId = user.userId;
        dto.email = user.email;
        dto.oauthProvider = user.oauthProvider;
        dto.displayName = user.displayName;
        dto.avatarUrl = user.avatarUrl;
        dto.subscriptionTier = user.subscriptionTier;
        dto.createdAt = user.createdAt;
        dto.updatedAt = user.updatedAt;
        // Note: oauthSubject and deletedAt are intentionally excluded

        return dto;
    }

    /**
     * Converts a UserPreference entity to UserPreferenceDTO for REST API responses.
     * Deserializes JSONB fields to structured objects.
     *
     * @param preference The user preference entity
     * @return UserPreferenceDTO with all fields mapped
     */
    public UserPreferenceDTO toPreferenceDTO(UserPreference preference) {
        if (preference == null) {
            return null;
        }

        UserPreferenceDTO dto = new UserPreferenceDTO();
        dto.userId = preference.userId;
        dto.defaultDeckType = preference.defaultDeckType;
        dto.theme = preference.theme;

        // Deserialize defaultRoomConfig JSONB to RoomConfigDTO
        dto.defaultRoomConfig = deserializeRoomConfig(preference.defaultRoomConfig);

        // Deserialize notificationSettings JSONB to NotificationSettingsDTO
        dto.notificationSettings = deserializeNotificationSettings(preference.notificationSettings);

        return dto;
    }

    /**
     * Converts UpdateUserPreferenceRequest to UserPreferenceConfig for service layer.
     * Combines all preference fields into a single config object.
     *
     * @param request The update request DTO
     * @return UserPreferenceConfig domain object
     */
    public UserPreferenceConfig toConfig(UpdateUserPreferenceRequest request) {
        if (request == null) {
            return UserPreferenceConfig.empty();
        }

        UserPreferenceConfig config = new UserPreferenceConfig();

        // Map defaultDeckType
        config.deckType = request.defaultDeckType;

        // Map defaultRoomConfig fields
        if (request.defaultRoomConfig != null) {
            RoomConfigDTO roomConfig = request.defaultRoomConfig;
            config.deckType = roomConfig.deckType != null ? roomConfig.deckType : config.deckType;
            config.timerEnabled = roomConfig.timerEnabled;
            config.timerDurationSeconds = roomConfig.timerDurationSeconds;
            config.revealBehavior = roomConfig.revealBehavior;
            config.allowObservers = roomConfig.allowObservers;
            config.allowSkip = true; // Default value, not exposed in OpenAPI yet
        }

        // Map notificationSettings fields
        if (request.notificationSettings != null) {
            NotificationSettingsDTO notifSettings = request.notificationSettings;
            config.emailNotifications = notifSettings.emailNotifications;
            // Map sessionReminders to roomInvites (closest match in config)
            config.roomInvites = notifSettings.sessionReminders;
            // Set defaults for other notification fields
            config.pushNotifications = false;
            config.votingComplete = true;
            config.sessionSummary = false;
        }

        return config;
    }

    /**
     * Deserializes JSONB defaultRoomConfig string to RoomConfigDTO.
     *
     * @param configJson The JSON string from database
     * @return RoomConfigDTO or null if deserialization fails
     */
    private RoomConfigDTO deserializeRoomConfig(String configJson) {
        if (configJson == null || configJson.isEmpty() || configJson.equals("{}")) {
            return null;
        }

        try {
            // Deserialize to UserPreferenceConfig first
            UserPreferenceConfig config = objectMapper.readValue(configJson, UserPreferenceConfig.class);

            // Convert to RoomConfigDTO
            RoomConfigDTO dto = new RoomConfigDTO();
            dto.deckType = config.deckType;
            dto.timerEnabled = config.timerEnabled;
            dto.timerDurationSeconds = config.timerDurationSeconds;
            dto.revealBehavior = config.revealBehavior;
            dto.allowObservers = config.allowObservers;
            dto.allowAnonymousVoters = true; // Default value
            dto.customDeck = null; // Not yet implemented

            return dto;
        } catch (JsonProcessingException e) {
            // Log error but don't fail the entire mapping
            System.err.println("Failed to deserialize room configuration: " + e.getMessage());
            return null;
        }
    }

    /**
     * Deserializes JSONB notificationSettings string to NotificationSettingsDTO.
     *
     * @param settingsJson The JSON string from database
     * @return NotificationSettingsDTO or null if deserialization fails
     */
    private NotificationSettingsDTO deserializeNotificationSettings(String settingsJson) {
        if (settingsJson == null || settingsJson.isEmpty() || settingsJson.equals("{}")) {
            return null;
        }

        try {
            // Deserialize to UserPreferenceConfig first
            UserPreferenceConfig config = objectMapper.readValue(settingsJson, UserPreferenceConfig.class);

            // Convert to NotificationSettingsDTO
            NotificationSettingsDTO dto = new NotificationSettingsDTO();
            dto.emailNotifications = config.emailNotifications;
            dto.sessionReminders = config.roomInvites; // Map roomInvites to sessionReminders

            return dto;
        } catch (JsonProcessingException e) {
            // Log error but don't fail the entire mapping
            System.err.println("Failed to deserialize notification settings: " + e.getMessage());
            return null;
        }
    }
}
