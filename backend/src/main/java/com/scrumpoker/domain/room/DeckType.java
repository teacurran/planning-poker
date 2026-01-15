package com.scrumpoker.domain.room;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enumeration of supported estimation deck types for rooms.
 * Serialized as lowercase strings for JSONB storage.
 */
public enum DeckType {
    FIBONACCI("fibonacci"),
    T_SHIRT("tshirt"),
    POWERS_OF_2("powers_of_2"),
    CUSTOM("custom");

    private final String jsonValue;

    DeckType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    /**
     * @return Lowercase value used for JSON serialization.
     */
    @JsonValue
    public String getJsonValue() {
        return jsonValue;
    }

    /**
     * Resolves a deck type from JSON or request payload values.
     *
     * @param value Deck type string (case insensitive)
     * @return Matching DeckType or default when value is blank
     */
    @JsonCreator
    public static DeckType fromJson(String value) {
        return fromValue(value);
    }

    /**
     * Resolves a deck type from arbitrary input values.
     *
     * @param value Deck type string
     * @return Matching DeckType
     */
    public static DeckType fromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DeckType.FIBONACCI;
        }

        String normalized = value.trim();
        for (DeckType deckType : values()) {
            if (deckType.jsonValue.equalsIgnoreCase(normalized)
                    || deckType.name().equalsIgnoreCase(normalized)) {
                return deckType;
            }
        }

        throw new IllegalArgumentException("Unsupported deck type: " + value);
    }
}
