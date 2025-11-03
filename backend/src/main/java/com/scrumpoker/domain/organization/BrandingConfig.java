package com.scrumpoker.domain.organization;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Branding configuration POJO for organizations.
 * Serialized to JSON and stored in Organization.branding JSONB field.
 *
 * Contains visual branding elements like logo URL and color scheme.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BrandingConfig {

    @JsonProperty("logo_url")
    private String logoUrl;

    @JsonProperty("primary_color")
    private String primaryColor;

    @JsonProperty("secondary_color")
    private String secondaryColor;

    /**
     * No-args constructor for Jackson deserialization.
     */
    public BrandingConfig() {
    }

    /**
     * All-args constructor for convenient object creation.
     *
     * @param logoUrl URL to organization logo image
     * @param primaryColor Primary brand color (hex format, e.g., "#FF5733")
     * @param secondaryColor Secondary brand color (hex format)
     */
    public BrandingConfig(String logoUrl, String primaryColor, String secondaryColor) {
        this.logoUrl = logoUrl;
        this.primaryColor = primaryColor;
        this.secondaryColor = secondaryColor;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    @Override
    public String toString() {
        return "BrandingConfig{" +
                "logoUrl='" + logoUrl + '\'' +
                ", primaryColor='" + primaryColor + '\'' +
                ", secondaryColor='" + secondaryColor + '\'' +
                '}';
    }
}
