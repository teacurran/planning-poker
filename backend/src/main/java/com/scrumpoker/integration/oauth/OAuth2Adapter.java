package com.scrumpoker.integration.oauth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * OAuth2 integration adapter service.
 * Provides a unified interface for OAuth2 authentication
 * across multiple providers.
 * Delegates to provider-specific implementations
 * (Google, Microsoft) based on the provider parameter.
 * <p>
 * This adapter implements the Strategy pattern where each
 * OAuth provider has its own implementation class handling
 * provider-specific OAuth2 flows and token validation.
 * </p>
 * <p>
 * Usage:
 * </p>
 * <ol>
 * <li>Frontend redirects user to OAuth provider's
 *     authorization URL with PKCE challenge</li>
 * <li>User authenticates and grants permissions</li>
 * <li>Provider redirects back to app with authorization code</li>
 * <li>Frontend calls REST endpoint which invokes this adapter's
 *     exchangeCodeForToken method</li>
 * <li>Adapter exchanges code for tokens, validates ID token,
 *     and returns user info</li>
 * <li>REST controller uses returned OAuthUserInfo to call
 *     UserService.findOrCreateUser()</li>
 * </ol>
 */
@ApplicationScoped
public class OAuth2Adapter {

    /**
     * Logger instance for this class.
     */
    private static final Logger LOG =
            Logger.getLogger(OAuth2Adapter.class);

    /**
     * Google OAuth provider implementation.
     */
    @Inject
    private GoogleOAuthProvider googleProvider;

    /**
     * Microsoft OAuth provider implementation.
     */
    @Inject
    private MicrosoftOAuthProvider microsoftProvider;

    /**
     * Exchanges OAuth2 authorization code for access token
     * and validates ID token.
     * Implements the OAuth2 Authorization Code Flow with PKCE.
     * <p>
     * This method:
     * </p>
     * <ol>
     * <li>Delegates to the appropriate provider implementation</li>
     * <li>Sends token request to provider's token endpoint</li>
     * <li>Validates the returned ID token (signature, expiration,
     *     issuer, audience)</li>
     * <li>Extracts user profile claims from ID token</li>
     * <li>Returns OAuthUserInfo DTO ready for JIT user
     *     provisioning</li>
     * </ol>
     *
     * @param providerName OAuth provider name
     *                     ("google" or "microsoft")
     * @param authorizationCode Authorization code from OAuth callback
     * @param codeVerifier PKCE code verifier
     *                     (client-generated random string)
     * @param redirectUri Redirect URI used in authorization request
     *                    (must match exactly)
     * @return OAuthUserInfo containing validated user profile data
     * @throws OAuth2AuthenticationException if provider is invalid
     *                                       or authentication fails
     * @throws IllegalArgumentException if required parameters
     *                                  are missing
     */
    public OAuthUserInfo exchangeCodeForToken(
            final String providerName,
            final String authorizationCode,
            final String codeVerifier,
            final String redirectUri) {
        // Validate inputs
        if (providerName == null
                || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Provider cannot be null or empty");
        }
        if (authorizationCode == null
                || authorizationCode.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Authorization code cannot be null or empty");
        }
        if (codeVerifier == null
                || codeVerifier.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Code verifier cannot be null or empty");
        }
        if (redirectUri == null
                || redirectUri.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Redirect URI cannot be null or empty");
        }

        LOG.infof("Processing OAuth2 token exchange for provider: %s",
                providerName);

        // Delegate to provider-specific implementation
        switch (providerName.toLowerCase()) {
            case "google":
                return googleProvider.exchangeCodeForToken(
                        authorizationCode, codeVerifier, redirectUri);

            case "microsoft":
                return microsoftProvider.exchangeCodeForToken(
                        authorizationCode, codeVerifier, redirectUri);

            default:
                throw new OAuth2AuthenticationException(
                        "Unsupported OAuth provider: " + providerName);
        }
    }

    /**
     * Validates an ID token and extracts user information.
     * This method is useful for validating tokens received
     * from the frontend or for re-validating cached tokens.
     *
     * @param providerName OAuth provider name
     *                     ("google" or "microsoft")
     * @param idToken JWT ID token to validate
     * @return OAuthUserInfo containing validated user profile data
     * @throws OAuth2AuthenticationException if provider is invalid
     *                                       or validation fails
     * @throws IllegalArgumentException if required parameters
     *                                  are missing
     */
    public OAuthUserInfo validateIdToken(
            final String providerName,
            final String idToken) {
        // Validate inputs
        if (providerName == null
                || providerName.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Provider cannot be null or empty");
        }
        if (idToken == null || idToken.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "ID token cannot be null or empty");
        }

        LOG.debugf("Validating ID token for provider: %s",
                providerName);

        // Delegate to provider-specific implementation
        switch (providerName.toLowerCase()) {
            case "google":
                return googleProvider.validateAndExtractClaims(idToken);

            case "microsoft":
                return microsoftProvider
                        .validateAndExtractClaims(idToken);

            default:
                throw new OAuth2AuthenticationException(
                        "Unsupported OAuth provider: " + providerName);
        }
    }

    /**
     * Gets the list of supported OAuth providers.
     *
     * @return Array of supported provider names
     */
    public String[] getSupportedProviders() {
        return new String[]{"google", "microsoft"};
    }

    /**
     * Checks if a given provider is supported.
     *
     * @param providerName Provider name to check
     * @return true if provider is supported, false otherwise
     */
    public boolean isProviderSupported(final String providerName) {
        if (providerName == null) {
            return false;
        }
        final String normalizedProvider = providerName.toLowerCase();
        return normalizedProvider.equals("google")
                || normalizedProvider.equals("microsoft");
    }
}
