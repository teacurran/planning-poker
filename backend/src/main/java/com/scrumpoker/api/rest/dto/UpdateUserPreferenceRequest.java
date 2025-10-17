package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for updating user preferences.
 * Matches OpenAPI UpdateUserPreferenceRequest schema.
 * All fields are optional.
 */
public class UpdateUserPreferenceRequest {

    @JsonProperty("defaultDeckType")
    public String defaultDeckType;

    @JsonProperty("defaultRoomConfig")
    public RoomConfigDTO defaultRoomConfig;

    @JsonProperty("theme")
    public String theme;

    @JsonProperty("notificationSettings")
    public NotificationSettingsDTO notificationSettings;
}
