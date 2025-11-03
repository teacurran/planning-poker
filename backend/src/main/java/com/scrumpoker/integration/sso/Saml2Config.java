package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashMap;
import java.util.Map;

/**
 * SAML2 provider configuration.
 * Contains IdP-specific settings for enterprise SAML2 authentication.
 * This configuration is nested within SsoConfig and stored in the
 * Organization.ssoConfig JSONB field.
 * <p>
 * Supports standard SAML2 IdPs like Azure AD, Okta, OneLogin,
 * and custom enterprise IdPs.
 * </p>
 */
public final class Saml2Config {

    /** Maximum length for entity ID. */
    private static final int MAX_ENTITY_ID_LENGTH = 500;

    /** Maximum length for URL. */
    private static final int MAX_URL_LENGTH = 500;

    /** Maximum length for certificate (PEM format). */
    private static final int MAX_CERTIFICATE_LENGTH = 5000;

    /**
     * IdP entity ID (unique identifier for the identity provider).
     * Example: "https://sts.windows.net/{tenant-id}/" (Azure AD)
     * or "http://www.okta.com/{org-id}" (Okta)
     */
    @NotNull
    @Size(max = MAX_ENTITY_ID_LENGTH)
    @JsonProperty("idpEntityId")
    private String idpEntityId;

    /**
     * SSO (Single Sign-On) URL for SAML authentication requests.
     * This is where the user is redirected for authentication.
     * Example: "https://login.microsoftonline.com/{tenant-id}/saml2"
     */
    @NotNull
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("ssoUrl")
    private String ssoUrl;

    /**
     * SLO (Single Logout) URL for logout requests.
     * Optional, used for backchannel logout.
     */
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("sloUrl")
    private String sloUrl;

    /**
     * IdP signing certificate in PEM format.
     * Used to validate SAML assertion signatures.
     * Example format:
     * -----BEGIN CERTIFICATE-----
     * MIIDPjCCAiagAwIBAgIQVWmXY/+9RqFA/OG9...
     * -----END CERTIFICATE-----
     * CRITICAL: This certificate MUST be validated to prevent
     * assertion forgery attacks.
     */
    @NotNull
    @Size(max = MAX_CERTIFICATE_LENGTH)
    @JsonProperty("certificate")
    private String certificate;

    /**
     * Attribute mapping from SAML assertion attributes to user fields.
     * Maps IdP-specific attribute names to our standard user fields.
     * <p>
     * Example mappings:
     * </p>
     * <pre>
     * {
     *   "email": "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress",
     *   "name": "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name",
     *   "groups": "http://schemas.microsoft.com/ws/2008/06/identity/claims/groups"
     * }
     * </pre>
     * <p>
     * Or for simpler IdPs:
     * </p>
     * <pre>
     * {
     *   "email": "email",
     *   "name": "displayName",
     *   "groups": "memberOf"
     * }
     * </pre>
     */
    @JsonProperty("attributeMapping")
    private Map<String, String> attributeMapping = new HashMap<>();

    /**
     * Whether to require signed assertions.
     * Default: true for security.
     */
    @JsonProperty("requireSignedAssertions")
    private boolean requireSignedAssertions = true;

    /**
     * Default constructor for Jackson deserialization.
     */
    public Saml2Config() {
    }

    /**
     * Constructor for creating Saml2Config instances.
     *
     * @param entityId IdP entity ID
     * @param singleSignOnUrl SSO URL
     * @param signingCertificate IdP signing certificate (PEM format)
     */
    public Saml2Config(final String entityId,
                       final String singleSignOnUrl,
                       final String signingCertificate) {
        this.idpEntityId = entityId;
        this.ssoUrl = singleSignOnUrl;
        this.certificate = signingCertificate;
    }

    // Getters and Setters

    /**
     * Gets the IdP entity ID.
     *
     * @return IdP entity ID
     */
    public String getIdpEntityId() {
        return idpEntityId;
    }

    /**
     * Sets the IdP entity ID.
     *
     * @param entityId IdP entity ID
     */
    public void setIdpEntityId(final String entityId) {
        this.idpEntityId = entityId;
    }

    /**
     * Gets the SSO URL.
     *
     * @return SSO URL
     */
    public String getSsoUrl() {
        return ssoUrl;
    }

    /**
     * Sets the SSO URL.
     *
     * @param url SSO URL
     */
    public void setSsoUrl(final String url) {
        this.ssoUrl = url;
    }

    /**
     * Gets the SLO URL.
     *
     * @return SLO URL or null if not configured
     */
    public String getSloUrl() {
        return sloUrl;
    }

    /**
     * Sets the SLO URL.
     *
     * @param url SLO URL
     */
    public void setSloUrl(final String url) {
        this.sloUrl = url;
    }

    /**
     * Gets the IdP signing certificate.
     *
     * @return IdP signing certificate (PEM format)
     */
    public String getCertificate() {
        return certificate;
    }

    /**
     * Sets the IdP signing certificate.
     *
     * @param cert IdP signing certificate (PEM format)
     */
    public void setCertificate(final String cert) {
        this.certificate = cert;
    }

    /**
     * Gets the attribute mapping.
     *
     * @return Attribute mapping from SAML to user fields
     */
    public Map<String, String> getAttributeMapping() {
        return attributeMapping;
    }

    /**
     * Sets the attribute mapping.
     *
     * @param mapping Attribute mapping from SAML to user fields
     */
    public void setAttributeMapping(final Map<String, String> mapping) {
        this.attributeMapping = mapping;
    }

    /**
     * Checks if signed assertions are required.
     *
     * @return true if signed assertions are required
     */
    public boolean isRequireSignedAssertions() {
        return requireSignedAssertions;
    }

    /**
     * Sets whether signed assertions are required.
     *
     * @param required true to require signed assertions
     */
    public void setRequireSignedAssertions(final boolean required) {
        this.requireSignedAssertions = required;
    }

    @Override
    public String toString() {
        return "Saml2Config{"
                + "idpEntityId='" + idpEntityId + '\''
                + ", ssoUrl='" + ssoUrl + '\''
                + ", sloUrl='" + sloUrl + '\''
                + ", certificate='***'"
                + ", attributeMapping=" + attributeMapping
                + ", requireSignedAssertions=" + requireSignedAssertions
                + '}';
    }
}
