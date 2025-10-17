package com.scrumpoker.integration.oauth;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Microsoft OAuth2 provider implementation.
 * Handles authorization code exchange and ID token validation for
 * Microsoft Identity Platform.
 * Uses Microsoft's OAuth2 endpoints: login.microsoftonline.com/common
 *
 * Microsoft Identity Platform supports both personal Microsoft accounts
 * and Azure AD organizational accounts via the /common tenant endpoint.
 */
@ApplicationScoped
public class MicrosoftOAuthProvider {

    /** Logger instance for this class. */
    private static final Logger LOG =
            Logger.getLogger(MicrosoftOAuthProvider.class);

    /** Microsoft provider identifier. */
    private static final String PROVIDER_NAME = "microsoft";

    /** Microsoft OAuth2 token endpoint URL. */
    private static final String TOKEN_ENDPOINT =
            "https://login.microsoftonline.com/common/oauth2/v2.0/token";

    /** Microsoft OAuth2 authorization server URL. */
    private static final String AUTH_SERVER_URL =
            "https://login.microsoftonline.com/common/v2.0";

    /** Connection timeout in seconds. */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** HTTP status code for successful response. */
    private static final int HTTP_OK = 200;

    /** Milliseconds per second conversion factor. */
    private static final long MILLIS_PER_SECOND = 1000L;

    /** Length of "id_token" prefix plus colon and quote. */
    private static final int ID_TOKEN_PREFIX_LENGTH = 11;

    /** Microsoft OAuth2 client ID. */
    @ConfigProperty(name = "quarkus.oidc.microsoft.client-id")
    private String clientId;

    /** Microsoft OAuth2 client secret. */
    @ConfigProperty(name = "quarkus.oidc.microsoft.credentials.secret")
    private String clientSecret;

    /** JWT parser for ID token validation. */
    @Inject
    private JWTParser jwtParser;

    /** HTTP client for token endpoint requests. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();

    /**
     * Exchanges authorization code for access token and ID token.
     * Implements OAuth2 Authorization Code Flow with PKCE.
     *
     * @param authorizationCode Authorization code from Microsoft OAuth
     *                          callback
     * @param codeVerifier PKCE code verifier used in the authorization
     *                     request
     * @param redirectUri Redirect URI that was used in authorization
     *                    request (must match)
     * @return OAuthUserInfo extracted from ID token claims
     * @throws OAuth2AuthenticationException if token exchange or
     *                                       validation fails
     */
    public OAuthUserInfo exchangeCodeForToken(
            final String authorizationCode,
            final String codeVerifier,
            final String redirectUri) {
        LOG.debugf("Exchanging authorization code for Microsoft OAuth token");

        try {
            // Build token request parameters
            Map<String, String> params = new HashMap<>();
            params.put("code", authorizationCode);
            params.put("client_id", clientId);
            params.put("client_secret", clientSecret);
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

            // Send token request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_ENDPOINT))
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
                        "Microsoft token exchange failed with status "
                        + "%d: %s",
                        response.statusCode(), response.body());
                throw new OAuth2AuthenticationException(
                        "Failed to exchange authorization code: "
                                + response.body(),
                        PROVIDER_NAME);
            }

            // Parse response to extract ID token
            String responseBody = response.body();
            String idToken = extractIdToken(responseBody);

            if (idToken == null || idToken.isEmpty()) {
                throw new OAuth2AuthenticationException(
                        "ID token not found in Microsoft token response",
                        PROVIDER_NAME);
            }

            // Validate and extract claims from ID token
            return validateAndExtractClaims(idToken);

        } catch (IOException | InterruptedException e) {
            LOG.error(
                    "Network error during Microsoft OAuth token exchange",
                    e);
            throw new OAuth2AuthenticationException(
                    "Network error during token exchange",
                    PROVIDER_NAME, null, e);
        }
    }

    /**
     * Validates ID token and extracts user information from claims.
     * Performs signature verification using Microsoft's JWKS endpoint
     * and validates expiration.
     *
     * Microsoft ID tokens include these standard claims:
     * - sub: Subject identifier (unique user ID)
     * - email: User's email (may be missing for some account types)
     * - name: User's display name
     * - picture: Profile picture URL (v2.0 endpoint)
     *
     * @param idToken JWT ID token from Microsoft
     * @return OAuthUserInfo with user profile data
     * @throws OAuth2AuthenticationException if token validation fails
     */
    public OAuthUserInfo validateAndExtractClaims(final String idToken) {
        LOG.debugf("Validating Microsoft ID token and extracting claims");

        try {
            // Parse and validate JWT token
            // JWTParser handles signature verification using JWKS
            // endpoint
            JsonWebToken jwt = jwtParser.parse(idToken);

            // Validate token expiration
            long currentTime = System.currentTimeMillis()
                    / MILLIS_PER_SECOND;
            if (jwt.getExpirationTime() < currentTime) {
                throw new OAuth2AuthenticationException(
                        "ID token has expired", PROVIDER_NAME);
            }

            // Validate issuer (Microsoft supports multiple issuer
            // formats)
            String issuer = jwt.getIssuer();
            if (issuer == null || !isValidMicrosoftIssuer(issuer)) {
                throw new OAuth2AuthenticationException(
                        "Invalid ID token issuer: " + issuer,
                        PROVIDER_NAME);
            }

            // Validate audience (must match client ID)
            if (!jwt.getAudience().contains(clientId)) {
                throw new OAuth2AuthenticationException(
                        "ID token audience does not match client ID",
                        PROVIDER_NAME);
            }

            // Extract claims
            String subject = jwt.getSubject();
            String email = jwt.getClaim("email");
            String name = jwt.getClaim("name");
            String picture = jwt.getClaim("picture");

            // Microsoft sometimes uses 'preferred_username' instead of 'email'
            if (email == null || email.isEmpty()) {
                email = jwt.getClaim("preferred_username");
            }

            if (subject == null || subject.isEmpty()) {
                throw new OAuth2AuthenticationException(
                        "Missing 'sub' claim in ID token",
                        PROVIDER_NAME);
            }
            if (email == null || email.isEmpty()) {
                throw new OAuth2AuthenticationException(
                        "Missing 'email' or 'preferred_username' claim "
                        + "in ID token",
                        PROVIDER_NAME);
            }

            // Use email as name fallback if name claim is missing
            if (name == null || name.isEmpty()) {
                name = email.split("@")[0];
            }

            LOG.infof(
                    "Successfully validated Microsoft OAuth token for "
                    + "user: %s", email);

            return new OAuthUserInfo(subject, email, name, picture,
                    PROVIDER_NAME);

        } catch (ParseException e) {
            LOG.error(
                    "Failed to parse or validate Microsoft ID token", e);
            throw new OAuth2AuthenticationException(
                    "Invalid ID token: " + e.getMessage(),
                    PROVIDER_NAME, null, e);
        }
    }

    /**
     * Validates Microsoft issuer claim.
     * Microsoft uses different issuer URLs depending on tenant type:
     * - https://login.microsoftonline.com/{tenantid}/v2.0
     * - https://login.microsoftonline.com/common/v2.0
     * - https://sts.windows.net/{tenantid}/
     *
     * @param issuer Issuer claim from ID token
     * @return true if issuer is valid Microsoft issuer
     */
    private boolean isValidMicrosoftIssuer(final String issuer) {
        return issuer.startsWith("https://login.microsoftonline.com/")
               || issuer.startsWith("https://sts.windows.net/");
    }

    /**
     * Extracts ID token from JSON token response.
     * Simple JSON parsing to avoid adding Jackson dependency just for
     * this.
     *
     * @param jsonResponse Token endpoint JSON response
     * @return ID token string
     */
    private String extractIdToken(final String jsonResponse) {
        // Simple JSON extraction - looks for "id_token":"..." pattern
        int startIndex = jsonResponse.indexOf("\"id_token\"");
        if (startIndex == -1) {
            return null;
        }

        startIndex = jsonResponse.indexOf("\"",
                startIndex + ID_TOKEN_PREFIX_LENGTH);
        if (startIndex == -1) {
            return null;
        }

        int endIndex = jsonResponse.indexOf("\"", startIndex + 1);
        if (endIndex == -1) {
            return null;
        }

        return jsonResponse.substring(startIndex + 1, endIndex);
    }

    /**
     * Gets the provider name identifier.
     *
     * @return Provider name ("microsoft")
     */
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
