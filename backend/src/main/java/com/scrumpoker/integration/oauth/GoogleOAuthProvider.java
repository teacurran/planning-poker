package com.scrumpoker.integration.oauth;

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
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
 * Google OAuth2 provider implementation.
 * Handles authorization code exchange and ID token validation for Google OAuth2 flow.
 * Uses Google's OAuth2 endpoints: accounts.google.com
 */
@ApplicationScoped
public class GoogleOAuthProvider {

    private static final Logger LOG = Logger.getLogger(GoogleOAuthProvider.class);

    private static final String PROVIDER_NAME = "google";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String AUTH_SERVER_URL = "https://accounts.google.com";

    @ConfigProperty(name = "quarkus.oidc.google.client-id")
    String clientId;

    @ConfigProperty(name = "quarkus.oidc.google.credentials.secret")
    String clientSecret;

    @Inject
    JWTParser jwtParser;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Exchanges authorization code for access token and ID token.
     * Implements OAuth2 Authorization Code Flow with PKCE.
     *
     * @param authorizationCode Authorization code from Google OAuth callback
     * @param codeVerifier PKCE code verifier used in the authorization request
     * @param redirectUri Redirect URI that was used in authorization request (must match)
     * @return OAuthUserInfo extracted from ID token claims
     * @throws OAuth2AuthenticationException if token exchange or validation fails
     */
    public OAuthUserInfo exchangeCodeForToken(String authorizationCode,
                                               String codeVerifier,
                                               String redirectUri) {
        LOG.debugf("Exchanging authorization code for Google OAuth token");

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
                    .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                              URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            // Send token request
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(formData))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                LOG.errorf("Google token exchange failed with status %d: %s",
                        response.statusCode(), response.body());
                throw new OAuth2AuthenticationException(
                        "Failed to exchange authorization code: " + response.body(),
                        PROVIDER_NAME);
            }

            // Parse response to extract ID token
            String responseBody = response.body();
            String idToken = extractIdToken(responseBody);

            if (idToken == null || idToken.isEmpty()) {
                throw new OAuth2AuthenticationException(
                        "ID token not found in Google token response",
                        PROVIDER_NAME);
            }

            // Validate and extract claims from ID token
            return validateAndExtractClaims(idToken);

        } catch (IOException | InterruptedException e) {
            LOG.error("Network error during Google OAuth token exchange", e);
            throw new OAuth2AuthenticationException(
                    "Network error during token exchange", PROVIDER_NAME, null, e);
        }
    }

    /**
     * Validates ID token and extracts user information from claims.
     * Performs signature verification using Google's JWKS endpoint and validates expiration.
     *
     * @param idToken JWT ID token from Google
     * @return OAuthUserInfo with user profile data
     * @throws OAuth2AuthenticationException if token validation fails
     */
    public OAuthUserInfo validateAndExtractClaims(String idToken) {
        LOG.debugf("Validating Google ID token and extracting claims");

        try {
            // Parse and validate JWT token
            // JWTParser handles signature verification using JWKS endpoint
            JsonWebToken jwt = jwtParser.parse(idToken);

            // Validate token expiration
            long currentTime = System.currentTimeMillis() / 1000;
            if (jwt.getExpirationTime() < currentTime) {
                throw new OAuth2AuthenticationException(
                        "ID token has expired", PROVIDER_NAME);
            }

            // Validate issuer
            String issuer = jwt.getIssuer();
            if (issuer == null || (!issuer.equals("https://accounts.google.com") &&
                    !issuer.equals("accounts.google.com"))) {
                throw new OAuth2AuthenticationException(
                        "Invalid ID token issuer: " + issuer, PROVIDER_NAME);
            }

            // Validate audience (must match client ID)
            if (!jwt.getAudience().contains(clientId)) {
                throw new OAuth2AuthenticationException(
                        "ID token audience does not match client ID", PROVIDER_NAME);
            }

            // Extract claims
            String subject = jwt.getSubject();
            String email = jwt.getClaim("email");
            String name = jwt.getClaim("name");
            String picture = jwt.getClaim("picture");

            if (subject == null || subject.isEmpty()) {
                throw new OAuth2AuthenticationException(
                        "Missing 'sub' claim in ID token", PROVIDER_NAME);
            }
            if (email == null || email.isEmpty()) {
                throw new OAuth2AuthenticationException(
                        "Missing 'email' claim in ID token", PROVIDER_NAME);
            }

            // Use email as name fallback if name claim is missing
            if (name == null || name.isEmpty()) {
                name = email.split("@")[0];
            }

            LOG.infof("Successfully validated Google OAuth token for user: %s", email);

            return new OAuthUserInfo(subject, email, name, picture, PROVIDER_NAME);

        } catch (ParseException e) {
            LOG.error("Failed to parse or validate Google ID token", e);
            throw new OAuth2AuthenticationException(
                    "Invalid ID token: " + e.getMessage(), PROVIDER_NAME, null, e);
        }
    }

    /**
     * Extracts ID token from JSON token response.
     * Simple JSON parsing to avoid adding Jackson dependency just for this.
     *
     * @param jsonResponse Token endpoint JSON response
     * @return ID token string
     */
    private String extractIdToken(String jsonResponse) {
        // Simple JSON extraction - looks for "id_token":"..." pattern
        int startIndex = jsonResponse.indexOf("\"id_token\"");
        if (startIndex == -1) {
            return null;
        }

        startIndex = jsonResponse.indexOf("\"", startIndex + 11);
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
     * @return Provider name ("google")
     */
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
