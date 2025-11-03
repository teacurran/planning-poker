package com.scrumpoker.integration.sso;

/**
 * Exception thrown when SSO authentication fails.
 * This can occur during:
 * - OIDC authorization code exchange for access token
 * - OIDC ID token signature validation
 * - SAML2 assertion validation
 * - SAML2 certificate verification
 * - Invalid SSO provider configuration
 * - Missing or invalid attributes in ID token or assertion
 * - Organization SSO configuration not found
 * - Domain verification failure
 * <p>
 * This exception should be mapped to HTTP 401 Unauthorized in the
 * REST controller layer.
 * </p>
 */
public final class SsoAuthenticationException extends RuntimeException {

    /**
     * SSO protocol that failed authentication.
     * Either "oidc" or "saml2".
     */
    private final String protocol;

    /**
     * Provider-specific error code.
     */
    private final String errorCode;

    /**
     * Creates a new SsoAuthenticationException with a message.
     *
     * @param errorMessage Error message describing the failure
     */
    public SsoAuthenticationException(final String errorMessage) {
        super(errorMessage);
        this.protocol = null;
        this.errorCode = null;
    }

    /**
     * Creates a new SsoAuthenticationException with a message and cause.
     *
     * @param errorMessage Error message describing the failure
     * @param cause The underlying exception that caused
     *              this authentication failure
     */
    public SsoAuthenticationException(
            final String errorMessage,
            final Throwable cause) {
        super(errorMessage, cause);
        this.protocol = null;
        this.errorCode = null;
    }

    /**
     * Creates a new SsoAuthenticationException with protocol context.
     *
     * @param errorMessage Error message describing the failure
     * @param ssoProtocol SSO protocol ("oidc" or "saml2")
     */
    public SsoAuthenticationException(
            final String errorMessage,
            final String ssoProtocol) {
        super(errorMessage);
        this.protocol = ssoProtocol;
        this.errorCode = null;
    }

    /**
     * Creates a new SsoAuthenticationException with full context.
     *
     * @param errorMessage Error message describing the failure
     * @param ssoProtocol SSO protocol ("oidc" or "saml2")
     * @param providerErrorCode Provider-specific error code
     * @param cause The underlying exception that caused
     *              this authentication failure
     */
    public SsoAuthenticationException(
            final String errorMessage,
            final String ssoProtocol,
            final String providerErrorCode,
            final Throwable cause) {
        super(errorMessage, cause);
        this.protocol = ssoProtocol;
        this.errorCode = providerErrorCode;
    }

    /**
     * Gets the SSO protocol that failed authentication.
     *
     * @return Protocol name or null if not specified
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Gets the provider-specific error code.
     *
     * @return Error code or null if not specified
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns a string representation of this exception including
     * protocol and error code context.
     * This method is designed for extension: subclasses can override
     * to add additional context.
     *
     * @return String representation with protocol and error code
     */
    @Override
    public String toString() {
        StringBuilder sb =
                new StringBuilder("SsoAuthenticationException: ");
        sb.append(getMessage());
        if (protocol != null) {
            sb.append(" [protocol=").append(protocol).append("]");
        }
        if (errorCode != null) {
            sb.append(" [errorCode=").append(errorCode).append("]");
        }
        return sb.toString();
    }
}
