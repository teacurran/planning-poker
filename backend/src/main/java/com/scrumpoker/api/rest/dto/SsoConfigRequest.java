package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for configuring SSO settings.
 * Includes sensitive data like clientSecret (only for updates, never returned).
 */
public class SsoConfigRequest {

    @NotBlank(message = "SSO protocol is required")
    @JsonProperty("protocol")
    public String protocol;

    @JsonProperty("issuer")
    public String issuer;

    @JsonProperty("clientId")
    public String clientId;

    @JsonProperty("clientSecret")
    public String clientSecret;

    @JsonProperty("authorizationEndpoint")
    public String authorizationEndpoint;

    @JsonProperty("tokenEndpoint")
    public String tokenEndpoint;

    @JsonProperty("jwksUri")
    public String jwksUri;

    @JsonProperty("samlEntityId")
    public String samlEntityId;

    @JsonProperty("samlSsoUrl")
    public String samlSsoUrl;

    @JsonProperty("samlCertificate")
    public String samlCertificate;
}
