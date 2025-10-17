package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.scrumpoker.domain.user.SubscriptionTier;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO for user resource in REST API responses.
 * Matches OpenAPI UserDTO schema definition.
 * Excludes sensitive fields (oauthSubject, deletedAt).
 */
public class UserDTO {

    @JsonProperty("userId")
    public UUID userId;

    @JsonProperty("email")
    public String email;

    @JsonProperty("oauthProvider")
    public String oauthProvider;

    @JsonProperty("displayName")
    public String displayName;

    @JsonProperty("avatarUrl")
    public String avatarUrl;

    @JsonProperty("subscriptionTier")
    public SubscriptionTier subscriptionTier;

    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant createdAt;

    @JsonProperty("updatedAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    public Instant updatedAt;
}
