/**
 * OAuth2 integration adapters for third-party identity providers.
 * <p>
 * This package provides OAuth2 authentication flows for Google
 * and Microsoft identity providers. It implements the OAuth2
 * Authorization Code Flow with PKCE (Proof Key for Code Exchange)
 * for secure browser-based authentication.
 * </p>
 * <p>
 * Main components:
 * </p>
 * <ul>
 * <li>{@link com.scrumpoker.integration.oauth.OAuth2Adapter} -
 *     Facade for OAuth2 operations across multiple providers</li>
 * <li>{@link com.scrumpoker.integration.oauth.GoogleOAuthProvider} -
 *     Google-specific OAuth2 implementation</li>
 * <li>{@link com.scrumpoker.integration.oauth.MicrosoftOAuthProvider} -
 *     Microsoft-specific OAuth2 implementation</li>
 * <li>{@link com.scrumpoker.integration.oauth.OAuthUserInfo} -
 *     DTO containing validated user profile information</li>
 * </ul>
 *
 * @since 1.0.0
 */
package com.scrumpoker.integration.oauth;
