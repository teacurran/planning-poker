package com.scrumpoker.domain.room;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Configuration POJO for room settings stored in JSONB config column.
 * Handles deck type, timer settings, and reveal behavior.
 */
public class RoomConfig {

    @JsonProperty("deck_type")
    private String deckType;

    @JsonProperty("timer_enabled")
    private boolean timerEnabled;

    @JsonProperty("timer_duration_seconds")
    private int timerDurationSeconds;

    @JsonProperty("reveal_behavior")
    private String revealBehavior;

    @JsonProperty("allow_observers")
    private boolean allowObservers;

    /**
     * Default constructor for Jackson deserialization.
     */
    public RoomConfig() {
        // Sensible defaults
        this.deckType = "FIBONACCI";
        this.timerEnabled = false;
        this.timerDurationSeconds = 60;
        this.revealBehavior = "MANUAL";
        this.allowObservers = true;
    }

    /**
     * Constructor with all fields.
     */
    public RoomConfig(String deckType, boolean timerEnabled, int timerDurationSeconds,
                      String revealBehavior, boolean allowObservers) {
        this.deckType = deckType;
        this.timerEnabled = timerEnabled;
        this.timerDurationSeconds = timerDurationSeconds;
        this.revealBehavior = revealBehavior;
        this.allowObservers = allowObservers;
    }

    // Getters and setters

    public String getDeckType() {
        return deckType;
    }

    public void setDeckType(String deckType) {
        this.deckType = deckType;
    }

    public boolean isTimerEnabled() {
        return timerEnabled;
    }

    public void setTimerEnabled(boolean timerEnabled) {
        this.timerEnabled = timerEnabled;
    }

    public int getTimerDurationSeconds() {
        return timerDurationSeconds;
    }

    public void setTimerDurationSeconds(int timerDurationSeconds) {
        this.timerDurationSeconds = timerDurationSeconds;
    }

    public String getRevealBehavior() {
        return revealBehavior;
    }

    public void setRevealBehavior(String revealBehavior) {
        this.revealBehavior = revealBehavior;
    }

    public boolean isAllowObservers() {
        return allowObservers;
    }

    public void setAllowObservers(boolean allowObservers) {
        this.allowObservers = allowObservers;
    }

    @Override
    public String toString() {
        return "RoomConfig{" +
                "deckType='" + deckType + '\'' +
                ", timerEnabled=" + timerEnabled +
                ", timerDurationSeconds=" + timerDurationSeconds +
                ", revealBehavior='" + revealBehavior + '\'' +
                ", allowObservers=" + allowObservers +
                '}';
    }
}
