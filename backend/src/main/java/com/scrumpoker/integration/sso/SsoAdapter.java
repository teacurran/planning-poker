package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.UUID;

/**
 * SSO integration adapter service.
 * Provides a unified interface for enterprise SSO authentication
 * using OIDC protocol.
 * Delegates to OIDC provider implementation based on the
 * organization's SSO configuration.
 * <p>
 * This adapter implements the Strategy pattern where the
 * OIDC protocol has its own implementation class handling
 * protocol-specific flows and validation.
 * </p>
 * <p>
 * Usage Flow:
 * </p>
 * <ol>
 * <li>Organization admin configures SSO in Organization.ssoConfig
 *     (IdP endpoints, certificates, attribute mappings)</li>
 * <li>User attempts to log in via SSO</li>
 * <li>REST controller retrieves organization by email domain</li>
 * <li>REST controller calls this adapter's authenticate method
 *     with organization's SSO config</li>
 * <li>Adapter routes to OidcProvider or Saml2Provider based
 *     on protocol</li>
 * <li>Provider validates assertion/token and extracts user info</li>
 * <li>REST controller uses returned SsoUserInfo to call
 *     UserService.findOrCreateUser() for JIT provisioning</li>
 * <li>REST controller generates JWT tokens via JwtTokenService</li>
 * </ol>
 * <p>
 * Key Differences from OAuth2Adapter:
 * </p>
 * <ul>
 * <li>Organization-scoped (config loaded from database,
 *     not application.properties)</li>
 * <li>Returns SsoUserInfo with organizationId
 *     (not OAuthUserInfo)</li>
 * <li>Supports per-org attribute mapping for SAML2</li>
 * <li>Handles domain verification for JIT provisioning</li>
 * </ul>
 */
@ApplicationScoped
public class SsoAdapter {

    /**
     * Logger instance for this class.
     */
    private static final Logger LOG =
            Logger.getLogger(SsoAdapter.class);

    /**
     * OIDC provider implementation.
     */
    @Inject
    private OidcProvider oidcProvider;

    /**
     * Jackson ObjectMapper for JSON serialization/deserialization.
     */
    @Inject
    private ObjectMapper objectMapper;

    /**
     * Authenticates user via SSO and extracts user information.
     * This is the main entry point for SSO authentication.
     * <p>
     * For OIDC: Exchanges authorization code for ID token,
     * validates token, extracts claims.
     * </p>
     *
     * @param ssoConfigJson Organization's SSO configuration as JSON string
     *                      (from Organization.ssoConfig field)
     * @param authenticationData Authorization code from OIDC callback
     * @param additionalParams Additional parameters (codeVerifier, redirectUri)
     * @param organizationId Organization ID owning this SSO config
     * @return Uni emitting SsoUserInfo with user profile data
     *         and organization context
     * @throws SsoAuthenticationException if authentication fails or
     *                                    config is invalid
     * @throws IllegalArgumentException if required parameters are missing
     */
    public Uni<SsoUserInfo> authenticate(
            final String ssoConfigJson,
            final String authenticationData,
            final SsoAuthParams additionalParams,
            final UUID organizationId) {

        // Validate inputs
        if (ssoConfigJson == null || ssoConfigJson.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "SSO configuration cannot be null or empty");
        }
        if (authenticationData == null
                || authenticationData.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Authentication data cannot be null or empty");
        }
        if (organizationId == null) {
            throw new IllegalArgumentException(
                    "Organization ID cannot be null");
        }

        // Parse SSO configuration from JSON
        SsoConfig ssoConfig = parseSsoConfig(ssoConfigJson);

        LOG.infof("Processing SSO authentication for organization %s "
                + "using protocol: %s",
                organizationId, ssoConfig.getProtocol());

        // Validate protocol is OIDC
        String protocol = ssoConfig.getProtocol().toLowerCase();
        if (!"oidc".equals(protocol)) {
            throw new SsoAuthenticationException(
                    "Unsupported SSO protocol: " + protocol
                    + ". Only OIDC is currently supported.");
        }

        return authenticateOidc(authenticationData, additionalParams,
                ssoConfig.getOidc(), organizationId);
    }

    /**
     * Authenticates user via OIDC.
     * Delegates to OidcProvider for authorization code exchange
     * and token validation.
     *
     * @param authorizationCode Authorization code from OIDC callback
     * @param params Additional parameters (codeVerifier, redirectUri)
     * @param oidcConfig OIDC configuration
     * @param organizationId Organization ID
     * @return Uni emitting SsoUserInfo
     */
    private Uni<SsoUserInfo> authenticateOidc(
            final String authorizationCode,
            final SsoAuthParams params,
            final OidcConfig oidcConfig,
            final UUID organizationId) {

        // Validate OIDC-specific parameters
        if (params == null || params.getCodeVerifier() == null
                || params.getRedirectUri() == null) {
            throw new IllegalArgumentException(
                    "OIDC authentication requires codeVerifier "
                    + "and redirectUri");
        }

        if (oidcConfig == null) {
            throw new SsoAuthenticationException(
                    "OIDC configuration not found in SSO config",
                    "oidc");
        }

        return oidcProvider.exchangeCodeForToken(
                authorizationCode,
                params.getCodeVerifier(),
                params.getRedirectUri(),
                oidcConfig,
                organizationId);
    }

    /**
     * Parses SSO configuration from JSON string.
     * Deserializes Organization.ssoConfig JSONB field into SsoConfig POJO.
     *
     * @param ssoConfigJson JSON string from database
     * @return SsoConfig instance
     * @throws SsoAuthenticationException if JSON parsing fails
     */
    private SsoConfig parseSsoConfig(final String ssoConfigJson) {
        try {
            SsoConfig config = objectMapper.readValue(ssoConfigJson,
                    SsoConfig.class);

            // Validate protocol is specified
            if (config.getProtocol() == null
                    || config.getProtocol().isEmpty()) {
                throw new SsoAuthenticationException(
                        "SSO protocol not specified in configuration");
            }

            return config;

        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse SSO configuration JSON", e);
            throw new SsoAuthenticationException(
                    "Invalid SSO configuration: " + e.getMessage(),
                    null, null, e);
        }
    }

    /**
     * Initiates SSO logout (backchannel logout).
     * Calls the IdP's logout endpoint to invalidate the SSO session.
     *
     * @param ssoConfigJson Organization's SSO configuration as JSON string
     * @param protocol SSO protocol (must be "oidc")
     * @param logoutParams Logout parameters (idTokenHint, postLogoutRedirectUri)
     * @return Uni emitting true if logout successful
     */
    public Uni<Boolean> logout(
            final String ssoConfigJson,
            final String protocol,
            final SsoLogoutParams logoutParams) {

        // Parse SSO configuration
        SsoConfig ssoConfig = parseSsoConfig(ssoConfigJson);

        LOG.infof("Processing SSO logout using protocol: %s", protocol);

        // Validate protocol is OIDC
        if (!"oidc".equalsIgnoreCase(protocol)) {
            throw new SsoAuthenticationException(
                    "Unsupported SSO protocol: " + protocol
                    + ". Only OIDC is currently supported.");
        }

        if (ssoConfig.getOidc() == null) {
            throw new SsoAuthenticationException(
                    "OIDC configuration not found", "oidc");
        }

        return oidcProvider.logout(
                ssoConfig.getOidc(),
                logoutParams.getIdTokenHint(),
                logoutParams.getPostLogoutRedirectUri());
    }

    /**
     * Gets the list of supported SSO protocols.
     *
     * @return Array of supported protocol names
     */
    public String[] getSupportedProtocols() {
        return new String[]{"oidc"};
    }

    /**
     * Checks if a given protocol is supported.
     *
     * @param protocol Protocol name to check
     * @return true if protocol is supported, false otherwise
     */
    public boolean isProtocolSupported(final String protocol) {
        if (protocol == null) {
            return false;
        }
        return "oidc".equalsIgnoreCase(protocol);
    }

    /**
     * Additional parameters for SSO authentication.
     * Used to pass protocol-specific parameters to the adapter.
     */
    public static final class SsoAuthParams {
        /** OIDC: PKCE code verifier. */
        private String codeVerifier;

        /** OIDC: Redirect URI used in authorization request. */
        private String redirectUri;

        /**
         * Default constructor.
         */
        public SsoAuthParams() {
        }

        /**
         * Constructor for OIDC parameters.
         *
         * @param pkceCodeVerifier PKCE code verifier
         * @param authRedirectUri Redirect URI
         */
        public SsoAuthParams(final String pkceCodeVerifier,
                             final String authRedirectUri) {
            this.codeVerifier = pkceCodeVerifier;
            this.redirectUri = authRedirectUri;
        }

        /**
         * Gets the code verifier.
         *
         * @return Code verifier
         */
        public String getCodeVerifier() {
            return codeVerifier;
        }

        /**
         * Sets the code verifier.
         *
         * @param verifier Code verifier
         */
        public void setCodeVerifier(final String verifier) {
            this.codeVerifier = verifier;
        }

        /**
         * Gets the redirect URI.
         *
         * @return Redirect URI
         */
        public String getRedirectUri() {
            return redirectUri;
        }

        /**
         * Sets the redirect URI.
         *
         * @param uri Redirect URI
         */
        public void setRedirectUri(final String uri) {
            this.redirectUri = uri;
        }
    }

    /**
     * Parameters for SSO logout.
     * Contains protocol-specific logout parameters.
     */
    public static final class SsoLogoutParams {
        /** OIDC: ID token hint for logout. */
        private String idTokenHint;

        /** Post-logout redirect URI. */
        private String postLogoutRedirectUri;

        /** SAML2: NameID from assertion. */
        private String nameId;

        /** SAML2: Session index from assertion. */
        private String sessionIndex;

        /**
         * Default constructor.
         */
        public SsoLogoutParams() {
        }

        // Getters and Setters

        /**
         * Gets the ID token hint.
         *
         * @return ID token hint
         */
        public String getIdTokenHint() {
            return idTokenHint;
        }

        /**
         * Sets the ID token hint.
         *
         * @param hint ID token hint
         */
        public void setIdTokenHint(final String hint) {
            this.idTokenHint = hint;
        }

        /**
         * Gets the post-logout redirect URI.
         *
         * @return Post-logout redirect URI
         */
        public String getPostLogoutRedirectUri() {
            return postLogoutRedirectUri;
        }

        /**
         * Sets the post-logout redirect URI.
         *
         * @param uri Post-logout redirect URI
         */
        public void setPostLogoutRedirectUri(final String uri) {
            this.postLogoutRedirectUri = uri;
        }

        /**
         * Gets the NameID.
         *
         * @return NameID
         */
        public String getNameId() {
            return nameId;
        }

        /**
         * Sets the NameID.
         *
         * @param name NameID
         */
        public void setNameId(final String name) {
            this.nameId = name;
        }

        /**
         * Gets the session index.
         *
         * @return Session index
         */
        public String getSessionIndex() {
            return sessionIndex;
        }

        /**
         * Sets the session index.
         *
         * @param index Session index
         */
        public void setSessionIndex(final String index) {
            this.sessionIndex = index;
        }
    }
}
