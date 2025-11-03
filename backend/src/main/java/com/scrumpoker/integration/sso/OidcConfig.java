package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * OIDC (OpenID Connect) provider configuration.
 * Contains IdP-specific settings for enterprise OIDC authentication.
 * This configuration is nested within SsoConfig and stored in the
 * Organization.ssoConfig JSONB field.
 * <p>
 * Supports standard OIDC providers like Okta, Azure AD, Auth0,
 * and OneLogin.
 * </p>
 */
public final class OidcConfig {

    /** Maximum length for issuer URL. */
    private static final int MAX_URL_LENGTH = 500;

    /** Maximum length for client ID. */
    private static final int MAX_CLIENT_ID_LENGTH = 255;

    /** Maximum length for client secret. */
    private static final int MAX_CLIENT_SECRET_LENGTH = 500;

    /**
     * OIDC issuer URL (IdP's base URL).
     * Example: "https://your-org.okta.com"
     * or "https://login.microsoftonline.com/{tenant-id}/v2.0"
     */
    @NotNull
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("issuer")
    private String issuer;

    /**
     * OAuth2 client ID registered with the IdP.
     */
    @NotNull
    @Size(max = MAX_CLIENT_ID_LENGTH)
    @JsonProperty("clientId")
    private String clientId;

    /**
     * OAuth2 client secret.
     * SECURITY NOTE: This should be encrypted at rest in the database.
     */
    @NotNull
    @Size(max = MAX_CLIENT_SECRET_LENGTH)
    @JsonProperty("clientSecret")
    private String clientSecret;

    /**
     * Authorization endpoint URL.
     * Usually discovered via {issuer}/.well-known/openid-configuration
     * Example: "https://your-org.okta.com/oauth2/v1/authorize"
     */
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("authorizationEndpoint")
    private String authorizationEndpoint;

    /**
     * Token endpoint URL.
     * Usually discovered via {issuer}/.well-known/openid-configuration
     * Example: "https://your-org.okta.com/oauth2/v1/token"
     */
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("tokenEndpoint")
    private String tokenEndpoint;

    /**
     * UserInfo endpoint URL.
     * Usually discovered via {issuer}/.well-known/openid-configuration
     * Example: "https://your-org.okta.com/oauth2/v1/userinfo"
     */
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("userInfoEndpoint")
    private String userInfoEndpoint;

    /**
     * JWKS (JSON Web Key Set) URI for token signature verification.
     * Usually discovered via {issuer}/.well-known/openid-configuration
     * Example: "https://your-org.okta.com/oauth2/v1/keys"
     */
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("jwksUri")
    private String jwksUri;

    /**
     * Logout endpoint URL for backchannel logout.
     * Example: "https://your-org.okta.com/oauth2/v1/logout"
     */
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("logoutEndpoint")
    private String logoutEndpoint;

    /**
     * Default constructor for Jackson deserialization.
     */
    public OidcConfig() {
    }

    /**
     * Constructor for creating OidcConfig instances.
     *
     * @param issuerUrl OIDC issuer URL
     * @param oauthClientId OAuth2 client ID
     * @param oauthClientSecret OAuth2 client secret
     */
    public OidcConfig(final String issuerUrl,
                      final String oauthClientId,
                      final String oauthClientSecret) {
        this.issuer = issuerUrl;
        this.clientId = oauthClientId;
        this.clientSecret = oauthClientSecret;
    }

    // Getters and Setters

    /**
     * Gets the OIDC issuer URL.
     *
     * @return OIDC issuer URL
     */
    public String getIssuer() {
        return issuer;
    }

    /**
     * Sets the OIDC issuer URL.
     *
     * @param issuerUrl OIDC issuer URL
     */
    public void setIssuer(final String issuerUrl) {
        this.issuer = issuerUrl;
    }

    /**
     * Gets the OAuth2 client ID.
     *
     * @return OAuth2 client ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the OAuth2 client ID.
     *
     * @param oauthClientId OAuth2 client ID
     */
    public void setClientId(final String oauthClientId) {
        this.clientId = oauthClientId;
    }

    /**
     * Gets the OAuth2 client secret.
     *
     * @return OAuth2 client secret
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Sets the OAuth2 client secret.
     *
     * @param oauthClientSecret OAuth2 client secret
     */
    public void setClientSecret(final String oauthClientSecret) {
        this.clientSecret = oauthClientSecret;
    }

    /**
     * Gets the authorization endpoint URL.
     *
     * @return Authorization endpoint URL
     */
    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    /**
     * Sets the authorization endpoint URL.
     *
     * @param endpoint Authorization endpoint URL
     */
    public void setAuthorizationEndpoint(final String endpoint) {
        this.authorizationEndpoint = endpoint;
    }

    /**
     * Gets the token endpoint URL.
     *
     * @return Token endpoint URL
     */
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    /**
     * Sets the token endpoint URL.
     *
     * @param endpoint Token endpoint URL
     */
    public void setTokenEndpoint(final String endpoint) {
        this.tokenEndpoint = endpoint;
    }

    /**
     * Gets the UserInfo endpoint URL.
     *
     * @return UserInfo endpoint URL
     */
    public String getUserInfoEndpoint() {
        return userInfoEndpoint;
    }

    /**
     * Sets the UserInfo endpoint URL.
     *
     * @param endpoint UserInfo endpoint URL
     */
    public void setUserInfoEndpoint(final String endpoint) {
        this.userInfoEndpoint = endpoint;
    }

    /**
     * Gets the JWKS URI.
     *
     * @return JWKS URI
     */
    public String getJwksUri() {
        return jwksUri;
    }

    /**
     * Sets the JWKS URI.
     *
     * @param uri JWKS URI
     */
    public void setJwksUri(final String uri) {
        this.jwksUri = uri;
    }

    /**
     * Gets the logout endpoint URL.
     *
     * @return Logout endpoint URL
     */
    public String getLogoutEndpoint() {
        return logoutEndpoint;
    }

    /**
     * Sets the logout endpoint URL.
     *
     * @param endpoint Logout endpoint URL
     */
    public void setLogoutEndpoint(final String endpoint) {
        this.logoutEndpoint = endpoint;
    }

    @Override
    public String toString() {
        return "OidcConfig{"
                + "issuer='" + issuer + '\''
                + ", clientId='" + clientId + '\''
                + ", clientSecret='***'"
                + ", authorizationEndpoint='" + authorizationEndpoint + '\''
                + ", tokenEndpoint='" + tokenEndpoint + '\''
                + ", userInfoEndpoint='" + userInfoEndpoint + '\''
                + ", jwksUri='" + jwksUri + '\''
                + ", logoutEndpoint='" + logoutEndpoint + '\''
                + '}';
    }
}
