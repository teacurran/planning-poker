package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;

/**
 * Data Transfer Object for organization branding customization.
 */
public class BrandingDTO {

    @JsonProperty("logoUrl")
    public String logoUrl;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Primary color must be a valid hex color")
    @JsonProperty("primaryColor")
    public String primaryColor;

    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Secondary color must be a valid hex color")
    @JsonProperty("secondaryColor")
    public String secondaryColor;
}
