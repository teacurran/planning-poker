package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO for notification settings (part of UserPreferenceDTO).
 * Matches OpenAPI notificationSettings schema.
 */
public class NotificationSettingsDTO {

    @JsonProperty("emailNotifications")
    public Boolean emailNotifications;

    @JsonProperty("sessionReminders")
    public Boolean sessionReminders;
}
