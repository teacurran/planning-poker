package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * DTO for user preference resource in REST API responses.
 * Matches OpenAPI UserPreferenceDTO schema definition.
 */
public class UserPreferenceDTO {

    @JsonProperty("userId")
    public UUID userId;

    @JsonProperty("defaultDeckType")
    public String defaultDeckType;

    @JsonProperty("defaultRoomConfig")
    public RoomConfigDTO defaultRoomConfig;

    @JsonProperty("theme")
    public String theme;

    @JsonProperty("notificationSettings")
    public NotificationSettingsDTO notificationSettings;
}
