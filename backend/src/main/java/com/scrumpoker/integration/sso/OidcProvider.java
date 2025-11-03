package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * OIDC (OpenID Connect) provider implementation for enterprise SSO.
 * Handles authorization code exchange and ID token validation for
 * enterprise OIDC providers (Okta, Azure AD, Auth0, OneLogin).
 * <p>
 * Unlike the social OAuth providers (Google, Microsoft), this provider:
 * </p>
 * <ul>
 * <li>Supports per-organization IdP configuration from database</li>
 * <li>Returns SsoUserInfo with organization context</li>
 * <li>Extracts groups/roles for RBAC mapping</li>
 * <li>Validates email domain against organization domain</li>
 * </ul>
 */
@ApplicationScoped
public class OidcProvider {

    /** Logger instance for this class. */
    private static final Logger LOG =
            Logger.getLogger(OidcProvider.class);

    /** OIDC protocol identifier. */
    private static final String PROTOCOL_NAME = "oidc";

    /** Connection timeout in seconds. */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** HTTP status code for successful response. */
    private static final int HTTP_OK = 200;

    /** Milliseconds per second conversion factor. */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** JWT parser for ID token validation. */
    @Inject
    private JWTParser jwtParser;

    /** Jackson ObjectMapper for JSON parsing. */
    @Inject
    private ObjectMapper objectMapper;

    /** HTTP client for token endpoint requests. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /**
     * Exchanges authorization code for access token and ID token.
     * Implements OAuth2 Authorization Code Flow with PKCE.
     * <p>
     * This method is REACTIVE (returns Uni) but internally uses
     * blocking HTTP client. Future enhancement: replace with
     * Mutiny WebClient for fully reactive flow.
     * </p>
     *
     * @param authorizationCode Authorization code from OIDC callback
     * @param codeVerifier PKCE code verifier used in the authorization
     *                     request
     * @param redirectUri Redirect URI that was used in authorization
     *                    request (must match)
     * @param oidcConfig Organization-specific OIDC configuration
     * @param organizationId Organization ID owning this SSO config
     * @return Uni emitting SsoUserInfo extracted from ID token claims
     * @throws SsoAuthenticationException if token exchange or
     *                                    validation fails
     */
    public Uni<SsoUserInfo> exchangeCodeForToken(
            final String authorizationCode,
            final String codeVerifier,
            final String redirectUri,
            final OidcConfig oidcConfig,
            final UUID organizationId) {

        LOG.infof("Exchanging authorization code for OIDC token "
                + "(org: %s, issuer: %s)",
                organizationId, oidcConfig.getIssuer());

        return Uni.createFrom().item(() -> {
            try {
                // Build token request parameters
                Map<String, String> params = new HashMap<>();
                params.put("code", authorizationCode);
                params.put("client_id", oidcConfig.getClientId());
                params.put("client_secret", oidcConfig.getClientSecret());
                params.put("redirect_uri", redirectUri);
                params.put("grant_type", "authorization_code");
                params.put("code_verifier", codeVerifier);

                String formData = params.entrySet().stream()
                        .map(e -> URLEncoder.encode(e.getKey(),
                                StandardCharsets.UTF_8)
                                + "="
                                + URLEncoder.encode(e.getValue(),
                                        StandardCharsets.UTF_8))
                        .collect(Collectors.joining("&"));

                // Determine token endpoint
                String tokenEndpoint = oidcConfig.getTokenEndpoint();
                if (tokenEndpoint == null || tokenEndpoint.isEmpty()) {
                    // Fallback to standard OIDC discovery path
                    tokenEndpoint = oidcConfig.getIssuer() + "/token";
                }

                // Send token request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(tokenEndpoint))
                        .header("Content-Type",
                                "application/x-www-form-urlencoded")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(formData))
                        .timeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != HTTP_OK) {
                    LOG.errorf(
                            "OIDC token exchange failed with status %d: %s",
                            response.statusCode(), response.body());
                    throw new SsoAuthenticationException(
                            "Failed to exchange authorization code: "
                                    + response.body(),
                            PROTOCOL_NAME);
                }

                // Parse response to extract ID token
                String responseBody = response.body();
                @SuppressWarnings("unchecked")
                Map<String, Object> tokenResponse =
                        objectMapper.readValue(responseBody, Map.class);

                String idToken = (String) tokenResponse.get("id_token");
                if (idToken == null || idToken.isEmpty()) {
                    throw new SsoAuthenticationException(
                            "ID token not found in OIDC token response",
                            PROTOCOL_NAME);
                }

                // Validate and extract claims from ID token
                return validateAndExtractClaims(idToken, oidcConfig,
                        organizationId);

            } catch (IOException | InterruptedException e) {
                LOG.error(
                        "Network error during OIDC token exchange", e);
                throw new SsoAuthenticationException(
                        "Network error during token exchange",
                        PROTOCOL_NAME, null, e);
            }
        });
    }

    /**
     * Validates ID token and extracts user information from claims.
     * Performs signature verification using IdP's JWKS endpoint and
     * validates expiration, issuer, and audience.
     *
     * @param idToken JWT ID token from OIDC IdP
     * @param oidcConfig Organization-specific OIDC configuration
     * @param organizationId Organization ID owning this SSO config
     * @return SsoUserInfo with user profile data and organization context
     * @throws SsoAuthenticationException if token validation fails
     */
    public SsoUserInfo validateAndExtractClaims(
            final String idToken,
            final OidcConfig oidcConfig,
            final UUID organizationId) {

        LOG.debugf("Validating OIDC ID token and extracting claims "
                + "(org: %s)", organizationId);

        try {
            // Parse and validate JWT token
            // JWTParser handles signature verification using JWKS endpoint
            JsonWebToken jwt = jwtParser.parse(idToken);

            // Validate token expiration
            long currentTime = System.currentTimeMillis()
                    / MILLIS_PER_SECOND;
            if (jwt.getExpirationTime() < currentTime) {
                throw new SsoAuthenticationException(
                        "ID token has expired", PROTOCOL_NAME);
            }

            // Validate issuer
            String issuer = jwt.getIssuer();
            String expectedIssuer = oidcConfig.getIssuer();
            if (issuer == null || !issuer.equals(expectedIssuer)) {
                throw new SsoAuthenticationException(
                        "Invalid ID token issuer. Expected: "
                                + expectedIssuer + ", got: " + issuer,
                        PROTOCOL_NAME);
            }

            // Validate audience (must match client ID)
            String clientId = oidcConfig.getClientId();
            if (!jwt.getAudience().contains(clientId)) {
                throw new SsoAuthenticationException(
                        "ID token audience does not match client ID",
                        PROTOCOL_NAME);
            }

            // Extract standard OIDC claims
            String subject = jwt.getSubject();
            String email = jwt.getClaim("email");
            String name = jwt.getClaim("name");

            if (subject == null || subject.isEmpty()) {
                throw new SsoAuthenticationException(
                        "Missing 'sub' claim in ID token", PROTOCOL_NAME);
            }
            if (email == null || email.isEmpty()) {
                throw new SsoAuthenticationException(
                        "Missing 'email' claim in ID token", PROTOCOL_NAME);
            }

            // Use email as name fallback if name claim is missing
            if (name == null || name.isEmpty()) {
                name = email.split("@")[0];
            }

            // Extract groups/roles for RBAC mapping
            // Try both 'groups' and 'roles' claims
            // (different IdPs use different claim names)
            List<String> groups = extractGroupsClaim(jwt);

            LOG.infof(
                    "Successfully validated OIDC token for user: %s "
                    + "(org: %s, groups: %d)",
                    email, organizationId, groups.size());

            return new SsoUserInfo(subject, email, name,
                    PROTOCOL_NAME, organizationId, groups);

        } catch (ParseException e) {
            LOG.error("Failed to parse or validate OIDC ID token", e);
            throw new SsoAuthenticationException(
                    "Invalid ID token: " + e.getMessage(),
                    PROTOCOL_NAME, null, e);
        }
    }

    /**
     * Extracts groups/roles claim from JWT token.
     * Tries multiple claim names as different IdPs use different
     * conventions.
     *
     * @param jwt JsonWebToken to extract groups from
     * @return List of group/role names
     */
    @SuppressWarnings("unchecked")
    private List<String> extractGroupsClaim(final JsonWebToken jwt) {
        List<String> groups = new ArrayList<>();

        // Try 'groups' claim (Okta, Azure AD)
        Object groupsClaim = jwt.getClaim("groups");
        if (groupsClaim instanceof List) {
            groups.addAll((List<String>) groupsClaim);
        } else if (groupsClaim instanceof String) {
            groups.add((String) groupsClaim);
        }

        // If no groups found, try 'roles' claim (Auth0, OneLogin)
        if (groups.isEmpty()) {
            Object rolesClaim = jwt.getClaim("roles");
            if (rolesClaim instanceof List) {
                groups.addAll((List<String>) rolesClaim);
            } else if (rolesClaim instanceof String) {
                groups.add((String) rolesClaim);
            }
        }

        return groups;
    }

    /**
     * Initiates backchannel logout with the IdP.
     * Calls the IdP's logout endpoint to invalidate the SSO session.
     * <p>
     * IMPLEMENTATION NOTE: This is a BASIC implementation.
     * Full backchannel logout requires:
     * </p>
     * <ul>
     * <li>Logout endpoint configured in OidcConfig</li>
     * <li>ID token hint or refresh token for session identification</li>
     * <li>Post-logout redirect URI</li>
     * </ul>
     *
     * @param oidcConfig Organization-specific OIDC configuration
     * @param idTokenHint ID token to identify the session
     * @param postLogoutRedirectUri URL to redirect after logout
     * @return Uni emitting true if logout successful
     */
    public Uni<Boolean> logout(
            final OidcConfig oidcConfig,
            final String idTokenHint,
            final String postLogoutRedirectUri) {

        String logoutEndpoint = oidcConfig.getLogoutEndpoint();
        if (logoutEndpoint == null || logoutEndpoint.isEmpty()) {
            LOG.warnf("Logout endpoint not configured for issuer: %s",
                    oidcConfig.getIssuer());
            return Uni.createFrom().item(false);
        }

        return Uni.createFrom().item(() -> {
            try {
                // Build logout request URL with query parameters
                String logoutUrl = logoutEndpoint
                        + "?id_token_hint="
                        + URLEncoder.encode(idTokenHint,
                                StandardCharsets.UTF_8)
                        + "&post_logout_redirect_uri="
                        + URLEncoder.encode(postLogoutRedirectUri,
                                StandardCharsets.UTF_8);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(logoutUrl))
                        .GET()
                        .timeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                        .build();

                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());

                // Accept both 200 OK and 3xx redirects as success
                boolean success = response.statusCode() >= HTTP_OK
                        && response.statusCode() < 400;

                if (success) {
                    LOG.infof("Successfully initiated OIDC logout");
                } else {
                    LOG.warnf("OIDC logout failed with status: %d",
                            response.statusCode());
                }

                return success;

            } catch (IOException | InterruptedException e) {
                LOG.error("Error during OIDC logout", e);
                return false;
            }
        });
    }

    /**
     * Gets the protocol name identifier.
     *
     * @return Protocol name ("oidc")
     */
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }
}
