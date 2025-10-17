package com.scrumpoker.integration.oauth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * OAuth2 integration adapter service.
 * Provides a unified interface for OAuth2 authentication across multiple providers.
 * Delegates to provider-specific implementations (Google, Microsoft) based on the provider parameter.
 *
 * This adapter implements the Strategy pattern where each OAuth provider has its own
 * implementation class handling provider-specific OAuth2 flows and token validation.
 *
 * Usage:
 * 1. Frontend redirects user to OAuth provider's authorization URL with PKCE challenge
 * 2. User authenticates and grants permissions
 * 3. Provider redirects back to app with authorization code
 * 4. Frontend calls REST endpoint which invokes this adapter's exchangeCodeForToken method
 * 5. Adapter exchanges code for tokens, validates ID token, and returns user info
 * 6. REST controller uses returned OAuthUserInfo to call UserService.findOrCreateUser()
 */
@ApplicationScoped
public class OAuth2Adapter {

    private static final Logger LOG = Logger.getLogger(OAuth2Adapter.class);

    @Inject
    GoogleOAuthProvider googleProvider;

    @Inject
    MicrosoftOAuthProvider microsoftProvider;

    /**
     * Exchanges OAuth2 authorization code for access token and validates ID token.
     * Implements the OAuth2 Authorization Code Flow with PKCE.
     *
     * This method:
     * 1. Delegates to the appropriate provider implementation
     * 2. Sends token request to provider's token endpoint
     * 3. Validates the returned ID token (signature, expiration, issuer, audience)
     * 4. Extracts user profile claims from ID token
     * 5. Returns OAuthUserInfo DTO ready for JIT user provisioning
     *
     * @param provider OAuth provider name ("google" or "microsoft")
     * @param authorizationCode Authorization code from OAuth callback
     * @param codeVerifier PKCE code verifier (client-generated random string)
     * @param redirectUri Redirect URI used in authorization request (must match exactly)
     * @return OAuthUserInfo containing validated user profile data
     * @throws OAuth2AuthenticationException if provider is invalid or authentication fails
     * @throws IllegalArgumentException if required parameters are missing
     */
    public OAuthUserInfo exchangeCodeForToken(String provider,
                                               String authorizationCode,
                                               String codeVerifier,
                                               String redirectUri) {
        // Validate inputs
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider cannot be null or empty");
        }
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Authorization code cannot be null or empty");
        }
        if (codeVerifier == null || codeVerifier.trim().isEmpty()) {
            throw new IllegalArgumentException("Code verifier cannot be null or empty");
        }
        if (redirectUri == null || redirectUri.trim().isEmpty()) {
            throw new IllegalArgumentException("Redirect URI cannot be null or empty");
        }

        LOG.infof("Processing OAuth2 token exchange for provider: %s", provider);

        // Delegate to provider-specific implementation
        switch (provider.toLowerCase()) {
            case "google":
                return googleProvider.exchangeCodeForToken(
                        authorizationCode, codeVerifier, redirectUri);

            case "microsoft":
                return microsoftProvider.exchangeCodeForToken(
                        authorizationCode, codeVerifier, redirectUri);

            default:
                throw new OAuth2AuthenticationException(
                        "Unsupported OAuth provider: " + provider);
        }
    }

    /**
     * Validates an ID token and extracts user information.
     * This method is useful for validating tokens received from the frontend
     * or for re-validating cached tokens.
     *
     * @param provider OAuth provider name ("google" or "microsoft")
     * @param idToken JWT ID token to validate
     * @return OAuthUserInfo containing validated user profile data
     * @throws OAuth2AuthenticationException if provider is invalid or validation fails
     * @throws IllegalArgumentException if required parameters are missing
     */
    public OAuthUserInfo validateIdToken(String provider, String idToken) {
        // Validate inputs
        if (provider == null || provider.trim().isEmpty()) {
            throw new IllegalArgumentException("Provider cannot be null or empty");
        }
        if (idToken == null || idToken.trim().isEmpty()) {
            throw new IllegalArgumentException("ID token cannot be null or empty");
        }

        LOG.debugf("Validating ID token for provider: %s", provider);

        // Delegate to provider-specific implementation
        switch (provider.toLowerCase()) {
            case "google":
                return googleProvider.validateAndExtractClaims(idToken);

            case "microsoft":
                return microsoftProvider.validateAndExtractClaims(idToken);

            default:
                throw new OAuth2AuthenticationException(
                        "Unsupported OAuth provider: " + provider);
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
     * @param provider Provider name to check
     * @return true if provider is supported, false otherwise
     */
    public boolean isProviderSupported(String provider) {
        if (provider == null) {
            return false;
        }
        String normalizedProvider = provider.toLowerCase();
        return normalizedProvider.equals("google") || normalizedProvider.equals("microsoft");
    }
}
