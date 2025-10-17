package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/**
 * DTO for room participants displayed in room details.
 * Matches OpenAPI RoomParticipantDTO schema.
 */
public class RoomParticipantDTO {

    @JsonProperty("userId")
    public UUID userId;

    @JsonProperty("displayName")
    public String displayName;

    @JsonProperty("avatarUrl")
    public String avatarUrl;

    @JsonProperty("role")
    public String role; // HOST, VOTER, OBSERVER
}
