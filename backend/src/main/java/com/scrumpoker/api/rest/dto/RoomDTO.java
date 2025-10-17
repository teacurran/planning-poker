package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * DTO for room resource in REST API responses.
 * Matches OpenAPI RoomDTO schema definition.
 */
public class RoomDTO {

    @JsonProperty("roomId")
    public String roomId;

    @JsonProperty("ownerId")
    public UUID ownerId;

    @JsonProperty("organizationId")
    public UUID organizationId;

    @JsonProperty("title")
    public String title;

    @JsonProperty("privacyMode")
    public String privacyMode; // PUBLIC, INVITE_ONLY, ORG_RESTRICTED

    @JsonProperty("config")
    public RoomConfigDTO config;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant createdAt;

    @JsonProperty("lastActiveAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant lastActiveAt;

    @JsonProperty("participants")
    public List<RoomParticipantDTO> participants;
}
