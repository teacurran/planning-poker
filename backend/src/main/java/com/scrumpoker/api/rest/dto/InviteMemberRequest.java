package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.scrumpoker.domain.organization.OrgRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for inviting a member to an organization.
 */
public class InviteMemberRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @JsonProperty("email")
    public String email;

    @NotNull(message = "Role is required")
    @JsonProperty("role")
    public OrgRole role;
}
