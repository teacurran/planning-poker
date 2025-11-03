package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * SSO configuration POJO for deserializing Organization.ssoConfig JSONB field.
 * Supports both OIDC and SAML2 protocols with per-organization configuration.
 * This configuration is stored in the database as JSON in the
 * Organization.ssoConfig field and loaded at runtime when processing
 * SSO authentication requests.
 * <p>
 * Example JSON structure:
 * </p>
 * <pre>
 * {
 *   "protocol": "oidc",
 *   "oidc": {
 *     "issuer": "https://your-org.okta.com",
 *     "clientId": "client-id",
 *     "clientSecret": "encrypted-secret",
 *     ...
 *   },
 *   "domainVerificationRequired": true,
 *   "jitProvisioningEnabled": true
 * }
 * </pre>
 */
public final class SsoConfig {

    /**
     * SSO protocol to use for this organization.
     * Either "oidc" or "saml2".
     */
    @NotNull
    @JsonProperty("protocol")
    private String protocol;

    /**
     * OIDC-specific configuration.
     * Populated when protocol is "oidc".
     */
    @Valid
    @JsonProperty("oidc")
    private OidcConfig oidc;

    /**
     * SAML2-specific configuration.
     * Populated when protocol is "saml2".
     */
    @Valid
    @JsonProperty("saml2")
    private Saml2Config saml2;

    /**
     * Whether email domain verification is required.
     * When true, users must have an email matching the
     * organization's domain to be auto-assigned.
     */
    @JsonProperty("domainVerificationRequired")
    private boolean domainVerificationRequired = true;

    /**
     * Whether Just-In-Time (JIT) user provisioning is enabled.
     * When true, users are automatically created on first SSO login.
     * When false, users must be pre-created in the system.
     */
    @JsonProperty("jitProvisioningEnabled")
    private boolean jitProvisioningEnabled = true;

    /**
     * Default constructor for Jackson deserialization.
     */
    public SsoConfig() {
    }

    /**
     * Constructor for creating SsoConfig instances.
     *
     * @param ssoProtocol SSO protocol ("oidc" or "saml2")
     * @param oidcConfig OIDC configuration (null if not using OIDC)
     * @param saml2Config SAML2 configuration (null if not using SAML2)
     * @param requireDomainVerification Whether domain verification
     *                                  is required
     * @param enableJitProvisioning Whether JIT provisioning is enabled
     */
    public SsoConfig(final String ssoProtocol,
                     final OidcConfig oidcConfig,
                     final Saml2Config saml2Config,
                     final boolean requireDomainVerification,
                     final boolean enableJitProvisioning) {
        this.protocol = ssoProtocol;
        this.oidc = oidcConfig;
        this.saml2 = saml2Config;
        this.domainVerificationRequired = requireDomainVerification;
        this.jitProvisioningEnabled = enableJitProvisioning;
    }

    // Getters and Setters

    /**
     * Gets the SSO protocol.
     *
     * @return SSO protocol ("oidc" or "saml2")
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Sets the SSO protocol.
     *
     * @param ssoProtocol SSO protocol ("oidc" or "saml2")
     */
    public void setProtocol(final String ssoProtocol) {
        this.protocol = ssoProtocol;
    }

    /**
     * Gets the OIDC configuration.
     *
     * @return OIDC configuration or null if not using OIDC
     */
    public OidcConfig getOidc() {
        return oidc;
    }

    /**
     * Sets the OIDC configuration.
     *
     * @param oidcConfig OIDC configuration
     */
    public void setOidc(final OidcConfig oidcConfig) {
        this.oidc = oidcConfig;
    }

    /**
     * Gets the SAML2 configuration.
     *
     * @return SAML2 configuration or null if not using SAML2
     */
    public Saml2Config getSaml2() {
        return saml2;
    }

    /**
     * Sets the SAML2 configuration.
     *
     * @param saml2Config SAML2 configuration
     */
    public void setSaml2(final Saml2Config saml2Config) {
        this.saml2 = saml2Config;
    }

    /**
     * Checks if domain verification is required.
     *
     * @return true if domain verification is required
     */
    public boolean isDomainVerificationRequired() {
        return domainVerificationRequired;
    }

    /**
     * Sets whether domain verification is required.
     *
     * @param required true to require domain verification
     */
    public void setDomainVerificationRequired(final boolean required) {
        this.domainVerificationRequired = required;
    }

    /**
     * Checks if JIT provisioning is enabled.
     *
     * @return true if JIT provisioning is enabled
     */
    public boolean isJitProvisioningEnabled() {
        return jitProvisioningEnabled;
    }

    /**
     * Sets whether JIT provisioning is enabled.
     *
     * @param enabled true to enable JIT provisioning
     */
    public void setJitProvisioningEnabled(final boolean enabled) {
        this.jitProvisioningEnabled = enabled;
    }

    @Override
    public String toString() {
        return "SsoConfig{"
                + "protocol='" + protocol + '\''
                + ", oidc=" + oidc
                + ", saml2=" + saml2
                + ", domainVerificationRequired=" + domainVerificationRequired
                + ", jitProvisioningEnabled=" + jitProvisioningEnabled
                + '}';
    }
}
