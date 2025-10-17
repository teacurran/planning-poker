package com.scrumpoker.domain.user;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POJO for user preference JSONB fields: default_room_config
 * and notification_settings.
 * This class provides type-safe access to flexible configuration
 * stored as JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class UserPreferenceConfig {

    /** Deck type for poker estimation. */
    public String deckType;

    /** Whether timer is enabled for voting sessions. */
    public Boolean timerEnabled;

    /** Timer duration in seconds. */
    public Integer timerDurationSeconds;

    /** Reveal behavior: "manual" or "automatic". */
    public String revealBehavior;

    /** Whether observers are allowed in the room. */
    public Boolean allowObservers;

    /** Whether skip voting is allowed. */
    public Boolean allowSkip;

    /** Whether email notifications are enabled. */
    public Boolean emailNotifications;

    /** Whether push notifications are enabled. */
    public Boolean pushNotifications;

    /** Whether room invite notifications are enabled. */
    public Boolean roomInvites;

    /** Whether voting complete notifications are enabled. */
    public Boolean votingComplete;

    /** Whether session summary notifications are enabled. */
    public Boolean sessionSummary;

    /**
     * No-arg constructor for Jackson deserialization.
     */
    public UserPreferenceConfig() {
        // Jackson requires no-arg constructor
    }

    /**
     * Creates a default configuration with sensible defaults.
     *
     * @return UserPreferenceConfig with default values
     */
    public static UserPreferenceConfig defaultConfig() {
        final UserPreferenceConfig config = new UserPreferenceConfig();
        // Default room config
        final int defaultTimerSeconds = 300;
        config.deckType = "fibonacci";
        config.timerEnabled = false;
        config.timerDurationSeconds = defaultTimerSeconds;
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
     *
     * @return Empty UserPreferenceConfig
     */
    public static UserPreferenceConfig empty() {
        return new UserPreferenceConfig();
    }

    @Override
    public String toString() {
        return "UserPreferenceConfig{"
                + "deckType='" + deckType + '\''
                + ", timerEnabled=" + timerEnabled
                + ", timerDurationSeconds=" + timerDurationSeconds
                + ", revealBehavior='" + revealBehavior + '\''
                + ", allowObservers=" + allowObservers
                + ", allowSkip=" + allowSkip
                + ", emailNotifications=" + emailNotifications
                + ", pushNotifications=" + pushNotifications
                + ", roomInvites=" + roomInvites
                + ", votingComplete=" + votingComplete
                + ", sessionSummary=" + sessionSummary
                + '}';
    }
}
