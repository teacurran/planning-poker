package com.scrumpoker.integration.oauth;

/**
 * Exception thrown when OAuth2 authentication fails.
 * This can occur during:
 * - Authorization code exchange for access token
 * - ID token signature validation
 * - ID token expiration checks
 * - Invalid OAuth provider configuration
 * - Missing or invalid claims in ID token
 *
 * This exception should be mapped to HTTP 401 Unauthorized in the REST controller layer.
 */
public class OAuth2AuthenticationException extends RuntimeException {

    private final String provider;
    private final String errorCode;

    /**
     * Creates a new OAuth2AuthenticationException with a message.
     *
     * @param message Error message describing the authentication failure
     */
    public OAuth2AuthenticationException(String message) {
        super(message);
        this.provider = null;
        this.errorCode = null;
    }

    /**
     * Creates a new OAuth2AuthenticationException with a message and cause.
     *
     * @param message Error message describing the authentication failure
     * @param cause The underlying exception that caused this authentication failure
     */
    public OAuth2AuthenticationException(String message, Throwable cause) {
        super(message, cause);
        this.provider = null;
        this.errorCode = null;
    }

    /**
     * Creates a new OAuth2AuthenticationException with provider context.
     *
     * @param message Error message describing the authentication failure
     * @param provider OAuth provider name (e.g., "google", "microsoft")
     */
    public OAuth2AuthenticationException(String message, String provider) {
        super(message);
        this.provider = provider;
        this.errorCode = null;
    }

    /**
     * Creates a new OAuth2AuthenticationException with full context.
     *
     * @param message Error message describing the authentication failure
     * @param provider OAuth provider name (e.g., "google", "microsoft")
     * @param errorCode Provider-specific error code
     * @param cause The underlying exception that caused this authentication failure
     */
    public OAuth2AuthenticationException(String message, String provider,
                                          String errorCode, Throwable cause) {
        super(message, cause);
        this.provider = provider;
        this.errorCode = errorCode;
    }

    /**
     * Gets the OAuth provider that failed authentication.
     *
     * @return Provider name or null if not specified
     */
    public String getProvider() {
        return provider;
    }

    /**
     * Gets the provider-specific error code.
     *
     * @return Error code or null if not specified
     */
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("OAuth2AuthenticationException: ");
        sb.append(getMessage());
        if (provider != null) {
            sb.append(" [provider=").append(provider).append("]");
        }
        if (errorCode != null) {
            sb.append(" [errorCode=").append(errorCode).append("]");
        }
        return sb.toString();
    }
}
