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
 * Supports standard SAML2 providers like Azure AD, Okta SAML,
 * and OneLogin.
 * </p>
 */
public final class Saml2Config {

    /** Maximum length for URL fields. */
    private static final int MAX_URL_LENGTH = 500;

    /** Maximum length for entity ID. */
    private static final int MAX_ENTITY_ID_LENGTH = 500;

    /** Maximum length for certificate. */
    private static final int MAX_CERTIFICATE_LENGTH = 5000;

    /**
     * IdP metadata URL for retrieving IdP configuration.
     * Example: "https://login.microsoftonline.com/{tenant-id}/
     * federationmetadata/2007-06/federationmetadata.xml"
     * or "https://your-org.okta.com/app/{app-id}/sso/saml/metadata"
     */
    @NotNull
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("idpMetadataUrl")
    private String idpMetadataUrl;

    /**
     * Service Provider entity ID (unique identifier for this SP).
     * Example: "https://app.scrumpoker.com/saml"
     * This must match the entity ID configured in the IdP.
     */
    @NotNull
    @Size(max = MAX_ENTITY_ID_LENGTH)
    @JsonProperty("spEntityId")
    private String spEntityId;

    /**
     * Assertion Consumer Service URL (ACS endpoint).
     * This is the callback URL where IdP sends SAML responses.
     * Example: "https://app.scrumpoker.com/api/v1/auth/sso/callback"
     */
    @NotNull
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("acsUrl")
    private String acsUrl;

    /**
     * IdP X.509 certificate for signature validation (PEM format).
     * Example: "-----BEGIN CERTIFICATE-----\n
     * MIIDdzCCAl+gAwIBAgIE...\n-----END CERTIFICATE-----"
     * SECURITY NOTE: This certificate is used to verify SAML
     * assertion signatures.
     */
    @NotNull
    @Size(max = MAX_CERTIFICATE_LENGTH)
    @JsonProperty("idpCertificate")
    private String idpCertificate;

    /**
     * Whether to sign SAML authentication requests.
     * Some IdPs require signed requests for enhanced security.
     * Default: false
     */
    @JsonProperty("signRequests")
    private boolean signRequests = false;

    /**
     * Attribute mapping from SAML attributes to user fields.
     * Maps standard user fields to IdP-specific SAML attribute names.
     * <p>
     * Example for Azure AD:
     * </p>
     * <pre>
     * {
     *   "email": "http://schemas.xmlsoap.org/ws/2005/05/identity/
     *            claims/emailaddress",
     *   "name": "http://schemas.xmlsoap.org/ws/2005/05/identity/
     *           claims/name",
     *   "groups": "http://schemas.microsoft.com/ws/2008/06/identity/
     *             claims/groups"
     * }
     * </pre>
     * <p>
     * Example for Okta:
     * </p>
     * <pre>
     * {
     *   "email": "email",
     *   "name": "displayName",
     *   "groups": "groups"
     * }
     * </pre>
     */
    @JsonProperty("attributeMapping")
    private Map<String, String> attributeMapping = new HashMap<>();

    /**
     * Single Logout (SLO) endpoint URL for backchannel logout.
     * Example: "https://login.microsoftonline.com/{tenant-id}/saml2"
     * or "https://your-org.okta.com/app/{app-id}/slo/saml"
     */
    @Size(max = MAX_URL_LENGTH)
    @JsonProperty("sloEndpoint")
    private String sloEndpoint;

    /**
     * Default constructor for Jackson deserialization.
     */
    public Saml2Config() {
    }

    /**
     * Constructor for creating Saml2Config instances.
     *
     * @param metadata IdP metadata URL
     * @param entityId Service Provider entity ID
     * @param acsEndpoint Assertion Consumer Service URL
     * @param certificate IdP X.509 certificate (PEM format)
     */
    public Saml2Config(final String metadata,
                       final String entityId,
                       final String acsEndpoint,
                       final String certificate) {
        this.idpMetadataUrl = metadata;
        this.spEntityId = entityId;
        this.acsUrl = acsEndpoint;
        this.idpCertificate = certificate;
    }

    // Getters and Setters

    /**
     * Gets the IdP metadata URL.
     *
     * @return IdP metadata URL
     */
    public String getIdpMetadataUrl() {
        return idpMetadataUrl;
    }

    /**
     * Sets the IdP metadata URL.
     *
     * @param metadata IdP metadata URL
     */
    public void setIdpMetadataUrl(final String metadata) {
        this.idpMetadataUrl = metadata;
    }

    /**
     * Gets the Service Provider entity ID.
     *
     * @return SP entity ID
     */
    public String getSpEntityId() {
        return spEntityId;
    }

    /**
     * Sets the Service Provider entity ID.
     *
     * @param entityId SP entity ID
     */
    public void setSpEntityId(final String entityId) {
        this.spEntityId = entityId;
    }

    /**
     * Gets the Assertion Consumer Service URL.
     *
     * @return ACS URL
     */
    public String getAcsUrl() {
        return acsUrl;
    }

    /**
     * Sets the Assertion Consumer Service URL.
     *
     * @param acsEndpoint ACS URL
     */
    public void setAcsUrl(final String acsEndpoint) {
        this.acsUrl = acsEndpoint;
    }

    /**
     * Gets the IdP X.509 certificate.
     *
     * @return IdP certificate (PEM format)
     */
    public String getIdpCertificate() {
        return idpCertificate;
    }

    /**
     * Sets the IdP X.509 certificate.
     *
     * @param certificate IdP certificate (PEM format)
     */
    public void setIdpCertificate(final String certificate) {
        this.idpCertificate = certificate;
    }

    /**
     * Checks if SAML requests should be signed.
     *
     * @return true if requests should be signed
     */
    public boolean isSignRequests() {
        return signRequests;
    }

    /**
     * Sets whether SAML requests should be signed.
     *
     * @param sign true to sign requests
     */
    public void setSignRequests(final boolean sign) {
        this.signRequests = sign;
    }

    /**
     * Gets the attribute mapping configuration.
     *
     * @return Attribute mapping map
     */
    public Map<String, String> getAttributeMapping() {
        return attributeMapping;
    }

    /**
     * Sets the attribute mapping configuration.
     *
     * @param mapping Attribute mapping map
     */
    public void setAttributeMapping(final Map<String, String> mapping) {
        this.attributeMapping = mapping;
    }

    /**
     * Gets the Single Logout endpoint URL.
     *
     * @return SLO endpoint URL
     */
    public String getSloEndpoint() {
        return sloEndpoint;
    }

    /**
     * Sets the Single Logout endpoint URL.
     *
     * @param endpoint SLO endpoint URL
     */
    public void setSloEndpoint(final String endpoint) {
        this.sloEndpoint = endpoint;
    }

    @Override
    public String toString() {
        return "Saml2Config{"
                + "idpMetadataUrl='" + idpMetadataUrl + '\''
                + ", spEntityId='" + spEntityId + '\''
                + ", acsUrl='" + acsUrl + '\''
                + ", idpCertificate='***'"
                + ", signRequests=" + signRequests
                + ", attributeMapping=" + attributeMapping
                + ", sloEndpoint='" + sloEndpoint + '\''
                + '}';
    }
}
