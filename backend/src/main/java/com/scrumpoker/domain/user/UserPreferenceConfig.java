package com.scrumpoker.domain.user;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POJO for user preference JSONB fields: default_room_config and notification_settings.
 * This class provides type-safe access to flexible configuration stored as JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserPreferenceConfig {

    // Default Room Configuration Fields
    public String deckType;
    public Boolean timerEnabled;
    public Integer timerDurationSeconds;
    public String revealBehavior; // "manual", "automatic"
    public Boolean allowObservers;
    public Boolean allowSkip;

    // Notification Settings Fields
    public Boolean emailNotifications;
    public Boolean pushNotifications;
    public Boolean roomInvites;
    public Boolean votingComplete;
    public Boolean sessionSummary;

    public UserPreferenceConfig() {
        // No-arg constructor for Jackson deserialization
    }

    /**
     * Creates a default configuration with sensible defaults.
     */
    public static UserPreferenceConfig defaultConfig() {
        UserPreferenceConfig config = new UserPreferenceConfig();
        // Default room config
        config.deckType = "fibonacci";
        config.timerEnabled = false;
        config.timerDurationSeconds = 300;
        config.revealBehavior = "manual";
        config.allowObservers = true;
        config.allowSkip = true;

        // Default notification settings
        config.emailNotifications = true;
        config.pushNotifications = false;
        config.roomInvites = true;
        config.votingComplete = true;
        config.sessionSummary = false;

        return config;
    }

    /**
     * Creates an empty configuration (all fields null).
     */
    public static UserPreferenceConfig empty() {
        return new UserPreferenceConfig();
    }

    @Override
    public String toString() {
        return "UserPreferenceConfig{" +
                "deckType='" + deckType + '\'' +
                ", timerEnabled=" + timerEnabled +
                ", timerDurationSeconds=" + timerDurationSeconds +
                ", revealBehavior='" + revealBehavior + '\'' +
                ", allowObservers=" + allowObservers +
                ", allowSkip=" + allowSkip +
                ", emailNotifications=" + emailNotifications +
                ", pushNotifications=" + pushNotifications +
                ", roomInvites=" + roomInvites +
                ", votingComplete=" + votingComplete +
                ", sessionSummary=" + sessionSummary +
                '}';
    }
}
