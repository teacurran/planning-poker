package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scrumpoker.domain.organization.OrgRole;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for organization member information.
 */
public class OrgMemberDTO {

    @NotNull
    @JsonProperty("userId")
    public UUID userId;

    @NotNull
    @JsonProperty("displayName")
    public String displayName;

    @NotNull
    @JsonProperty("email")
    public String email;

    @JsonProperty("avatarUrl")
    public String avatarUrl;

    @NotNull
    @JsonProperty("role")
    public OrgRole role;

    @NotNull
    @JsonProperty("joinedAt")
    public Instant joinedAt;
}
