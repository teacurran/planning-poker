package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * SSO configuration POJO for deserializing Organization.ssoConfig JSONB field.
 * Supports OIDC and SAML2 protocols with per-organization configuration.
 * This configuration is stored in the database as JSON in the
 * Organization.ssoConfig field and loaded at runtime when processing
 * SSO authentication requests.
 * <p>
 * Example JSON structure for OIDC:
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
 * <p>
 * Example JSON structure for SAML2:
 * </p>
 * <pre>
 * {
 *   "protocol": "saml2",
 *   "saml2": {
 *     "idpMetadataUrl": "https://idp.example.com/metadata",
 *     "spEntityId": "https://app.scrumpoker.com/saml",
 *     "acsUrl": "https://app.scrumpoker.com/api/v1/auth/sso/callback",
 *     "idpCertificate": "-----BEGIN CERTIFICATE-----...",
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
     * Supported values: "oidc", "saml2"
     */
    @NotNull
    @JsonProperty("protocol")
    private String protocol;

    /**
     * OIDC-specific configuration.
     * Required when protocol is "oidc".
     */
    @Valid
    @JsonProperty("oidc")
    private OidcConfig oidc;

    /**
     * SAML2-specific configuration.
     * Required when protocol is "saml2".
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
     * Constructor for creating SsoConfig instances with OIDC.
     *
     * @param ssoProtocol SSO protocol ("oidc")
     * @param oidcConfig OIDC configuration
     * @param requireDomainVerification Whether domain verification
     *                                  is required
     * @param enableJitProvisioning Whether JIT provisioning is enabled
     */
    public SsoConfig(final String ssoProtocol,
                     final OidcConfig oidcConfig,
                     final boolean requireDomainVerification,
                     final boolean enableJitProvisioning) {
        this.protocol = ssoProtocol;
        this.oidc = oidcConfig;
        this.domainVerificationRequired = requireDomainVerification;
        this.jitProvisioningEnabled = enableJitProvisioning;
    }

    /**
     * Constructor for creating SsoConfig instances with SAML2.
     *
     * @param ssoProtocol SSO protocol ("saml2")
     * @param saml2Conf SAML2 configuration
     * @param requireDomainVerification Whether domain verification
     *                                  is required
     * @param enableJitProvisioning Whether JIT provisioning is enabled
     */
    public SsoConfig(final String ssoProtocol,
                     final Saml2Config saml2Conf,
                     final boolean requireDomainVerification,
                     final boolean enableJitProvisioning) {
        this.protocol = ssoProtocol;
        this.saml2 = saml2Conf;
        this.domainVerificationRequired = requireDomainVerification;
        this.jitProvisioningEnabled = enableJitProvisioning;
    }

    // Getters and Setters

    /**
     * Gets the SSO protocol.
     *
     * @return SSO protocol ("oidc")
     */
    public String getProtocol() {
        return protocol;
    }

    /**
     * Sets the SSO protocol.
     *
     * @param ssoProtocol SSO protocol ("oidc")
     */
    public void setProtocol(final String ssoProtocol) {
        this.protocol = ssoProtocol;
    }

    /**
     * Gets the OIDC configuration.
     *
     * @return OIDC configuration
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
     * @return SAML2 configuration
     */
    public Saml2Config getSaml2() {
        return saml2;
    }

    /**
     * Sets the SAML2 configuration.
     *
     * @param saml2Conf SAML2 configuration
     */
    public void setSaml2(final Saml2Config saml2Conf) {
        this.saml2 = saml2Conf;
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
