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

    /**
     * OAuth provider name that failed authentication.
     */
    private final String provider;

    /**
     * Provider-specific error code.
     */
    private final String errorCode;

    /**
     * Creates a new OAuth2AuthenticationException with a message.
     *
     * @param errorMessage Error message describing the failure
     */
    public OAuth2AuthenticationException(final String errorMessage) {
        super(errorMessage);
        this.provider = null;
        this.errorCode = null;
    }

    /**
     * Creates a new OAuth2AuthenticationException
     * with a message and cause.
     *
     * @param errorMessage Error message describing the failure
     * @param cause The underlying exception that caused
     *              this authentication failure
     */
    public OAuth2AuthenticationException(
            final String errorMessage,
            final Throwable cause) {
        super(errorMessage, cause);
        this.provider = null;
        this.errorCode = null;
    }

    /**
     * Creates a new OAuth2AuthenticationException
     * with provider context.
     *
     * @param errorMessage Error message describing the failure
     * @param providerName OAuth provider name
     *                     (e.g., "google", "microsoft")
     */
    public OAuth2AuthenticationException(
            final String errorMessage,
            final String providerName) {
        super(errorMessage);
        this.provider = providerName;
        this.errorCode = null;
    }

    /**
     * Creates a new OAuth2AuthenticationException with full context.
     *
     * @param errorMessage Error message describing the failure
     * @param providerName OAuth provider name
     *                     (e.g., "google", "microsoft")
     * @param providerErrorCode Provider-specific error code
     * @param cause The underlying exception that caused
     *              this authentication failure
     */
    public OAuth2AuthenticationException(
            final String errorMessage,
            final String providerName,
            final String providerErrorCode,
            final Throwable cause) {
        super(errorMessage, cause);
        this.provider = providerName;
        this.errorCode = providerErrorCode;
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
