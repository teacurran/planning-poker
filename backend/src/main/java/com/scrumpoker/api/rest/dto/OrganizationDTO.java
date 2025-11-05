package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for organization information.
 * Used for API responses containing organization details.
 */
public class OrganizationDTO {

    @NotNull
    @JsonProperty("orgId")
    public UUID orgId;

    @NotNull
    @JsonProperty("name")
    public String name;

    @NotNull
    @JsonProperty("domain")
    public String domain;

    @JsonProperty("ssoConfig")
    public SsoConfigDTO ssoConfig;

    @JsonProperty("branding")
    public BrandingDTO branding;

    @JsonProperty("subscriptionId")
    public UUID subscriptionId;

    @JsonProperty("memberCount")
    public Integer memberCount;

    @NotNull
    @JsonProperty("createdAt")
    public Instant createdAt;

    @JsonProperty("updatedAt")
    public Instant updatedAt;
}
