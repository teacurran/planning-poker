package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new organization.
 */
public class CreateOrganizationRequest {

    @NotBlank(message = "Organization name is required")
    @Size(max = 255, message = "Organization name must not exceed 255 characters")
    @JsonProperty("name")
    public String name;

    @NotBlank(message = "Organization domain is required")
    @Size(max = 255, message = "Organization domain must not exceed 255 characters")
    @JsonProperty("domain")
    public String domain;

    @Valid
    @JsonProperty("branding")
    public BrandingDTO branding;
}
