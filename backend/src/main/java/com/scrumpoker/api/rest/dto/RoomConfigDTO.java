package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * DTO for room configuration settings stored in JSONB.
 * Matches OpenAPI RoomConfigDTO schema.
 */
public class RoomConfigDTO {

    @JsonProperty("deckType")
    public String deckType; // fibonacci, tshirt, powers_of_2, custom

    @JsonProperty("customDeck")
    public List<String> customDeck;

    @JsonProperty("timerEnabled")
    public Boolean timerEnabled;

    @JsonProperty("timerDurationSeconds")
    public Integer timerDurationSeconds;

    @JsonProperty("revealBehavior")
    public String revealBehavior; // manual, automatic, timer

    @JsonProperty("allowObservers")
    public Boolean allowObservers;

    @JsonProperty("allowAnonymousVoters")
    public Boolean allowAnonymousVoters;
}
