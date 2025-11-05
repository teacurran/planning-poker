package com.scrumpoker.api.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for SSO configuration (OIDC or SAML2).
 * Used in API responses. Excludes sensitive data like clientSecret.
 */
public class SsoConfigDTO {

    @JsonProperty("protocol")
    public String protocol;

    @JsonProperty("issuer")
    public String issuer;

    @JsonProperty("clientId")
    public String clientId;

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

    // Note: clientSecret is intentionally excluded from responses for security
}
