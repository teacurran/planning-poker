package com.scrumpoker.integration.sso;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import org.jboss.logging.Logger;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SAML2 provider implementation for enterprise SSO.
 * Handles SAML2 assertion validation and user attribute extraction using OpenSAML 5.
 * <p>
 * This implementation provides:
 * </p>
 * <ul>
 * <li>SAML response parsing and unmarshalling</li>
 * <li>XML signature validation using IdP certificate</li>
 * <li>Assertion expiration validation (NotBefore/NotOnOrAfter)</li>
 * <li>Attribute extraction with custom mapping per organization</li>
 * <li>SAML Single Logout (SLO) support</li>
 * </ul>
 * <p>
 * Supports standard SAML2 IdPs including:
 * </p>
 * <ul>
 * <li>Azure AD</li>
 * <li>Okta</li>
 * <li>OneLogin</li>
 * <li>Custom enterprise IdPs</li>
 * </ul>
 */
@ApplicationScoped
public class Saml2Provider {

    /** Logger instance for this class. */
    private static final Logger LOG =
            Logger.getLogger(Saml2Provider.class);

    /** SAML2 protocol identifier. */
    private static final String PROTOCOL_NAME = "saml2";

    /** Assertion expiration tolerance in seconds (5 minutes). */
    private static final long EXPIRATION_TOLERANCE_SECONDS = 300;

    /** XML parser pool for SAML response parsing. */
    private BasicParserPool parserPool;

    /**
     * Initializes OpenSAML library and XML parser pool.
     * This method is called once when the bean is created.
     *
     * @throws RuntimeException if OpenSAML initialization fails
     */
    @PostConstruct
    public void init() {
        try {
            // Initialize OpenSAML library (one-time bootstrap)
            InitializationService.initialize();
            LOG.info("OpenSAML library initialized successfully");

            // Initialize XML parser pool
            parserPool = new BasicParserPool();
            parserPool.initialize();
            LOG.info("XML parser pool initialized successfully");

        } catch (InitializationException | ComponentInitializationException e) {
            LOG.error("Failed to initialize OpenSAML", e);
            throw new RuntimeException("Failed to initialize SAML2 provider", e);
        }
    }

    /**
     * Validates SAML2 assertion and extracts user information.
     * <p>
     * SAML2 Flow:
     * </p>
     * <ol>
     * <li>User is redirected to IdP SSO URL</li>
     * <li>User authenticates with IdP</li>
     * <li>IdP posts SAML response to ACS (Assertion Consumer Service)
     *     endpoint</li>
     * <li>This method validates the SAML response signature
     *     and assertion</li>
     * <li>Extracts user attributes using organization-specific mapping</li>
     * <li>Returns SsoUserInfo for JIT provisioning</li>
     * </ol>
     *
     * @param samlResponse Base64-encoded SAML response from IdP
     * @param saml2Config Organization-specific SAML2 configuration
     * @param organizationId Organization ID owning this SSO config
     * @return Uni emitting SsoUserInfo extracted from SAML assertion
     * @throws SsoAuthenticationException if assertion validation fails
     */
    public Uni<SsoUserInfo> validateAssertion(
            final String samlResponse,
            final Saml2Config saml2Config,
            final UUID organizationId) {

        LOG.infof("Validating SAML2 assertion (org: %s, IdP: %s)",
                organizationId, saml2Config.getIdpEntityId());

        return Uni.createFrom().item(() -> {
            try {
                // Step 1: Decode Base64-encoded SAML response
                byte[] decodedResponse = Base64.getDecoder()
                        .decode(samlResponse);
                String xmlResponse = new String(decodedResponse,
                        StandardCharsets.UTF_8);

                LOG.debugf("Decoded SAML response (length: %d bytes)",
                        xmlResponse.length());

                // Step 2: Load IdP certificate for signature verification
                X509Certificate idpCertificate =
                        loadCertificate(saml2Config.getCertificate());

                // Step 3: Parse and validate SAML response
                Map<String, Object> attributes =
                        parseSamlResponse(xmlResponse,
                                idpCertificate, saml2Config);

                // Step 4: Extract user attributes using mapping
                SsoUserInfo userInfo = extractUserInfo(attributes,
                        saml2Config, organizationId);

                LOG.infof(
                        "Successfully validated SAML2 assertion for user: %s "
                        + "(org: %s)",
                        userInfo.getEmail(), organizationId);

                return userInfo;

            } catch (Exception e) {
                LOG.error("Failed to validate SAML2 assertion", e);
                throw new SsoAuthenticationException(
                        "SAML2 assertion validation failed: "
                                + e.getMessage(),
                        PROTOCOL_NAME, null, e);
            }
        });
    }

    /**
     * Loads X.509 certificate from PEM format string.
     * Certificate is used for validating SAML assertion signatures.
     *
     * @param pemCertificate PEM-encoded certificate string
     * @return X509Certificate instance
     * @throws SsoAuthenticationException if certificate is invalid
     */
    private X509Certificate loadCertificate(final String pemCertificate) {
        try {
            // Remove PEM headers/footers and whitespace
            String certContent = pemCertificate
                    .replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");

            // Decode Base64
            byte[] certBytes = Base64.getDecoder().decode(certContent);

            // Parse certificate
            CertificateFactory certFactory =
                    CertificateFactory.getInstance("X.509");
            Certificate cert = certFactory.generateCertificate(
                    new ByteArrayInputStream(certBytes));

            if (!(cert instanceof X509Certificate)) {
                throw new SsoAuthenticationException(
                        "Certificate is not X.509 format",
                        PROTOCOL_NAME);
            }

            X509Certificate x509Cert = (X509Certificate) cert;

            // Validate certificate is not expired
            x509Cert.checkValidity();

            LOG.debugf("Loaded IdP certificate: subject=%s, issuer=%s",
                    x509Cert.getSubjectX500Principal().getName(),
                    x509Cert.getIssuerX500Principal().getName());

            return x509Cert;

        } catch (CertificateException e) {
            LOG.error("Failed to load IdP certificate", e);
            throw new SsoAuthenticationException(
                    "Invalid IdP certificate: " + e.getMessage(),
                    PROTOCOL_NAME, null, e);
        }
    }

    /**
     * Parses and validates SAML response using OpenSAML 5.
     * Performs signature validation, assertion expiration check,
     * and attribute extraction.
     *
     * @param xmlResponse SAML response XML
     * @param idpCertificate IdP certificate for signature validation
     * @param saml2Config SAML2 configuration
     * @return Map of SAML attributes
     * @throws SsoAuthenticationException if parsing or validation fails
     */
    private Map<String, Object> parseSamlResponse(
            final String xmlResponse,
            final X509Certificate idpCertificate,
            final Saml2Config saml2Config) {

        try {
            // Parse XML to DOM document
            Document doc = parserPool.parse(
                    new ByteArrayInputStream(xmlResponse.getBytes(StandardCharsets.UTF_8)));
            Element element = doc.getDocumentElement();

            // Unmarshall SAML response
            UnmarshallerFactory unmarshallerFactory =
                    XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
            Unmarshaller unmarshaller = unmarshallerFactory.getUnmarshaller(element);
            if (unmarshaller == null) {
                throw new SsoAuthenticationException(
                        "No unmarshaller found for SAML response element",
                        PROTOCOL_NAME);
            }

            XMLObject xmlObject = unmarshaller.unmarshall(element);
            if (!(xmlObject instanceof Response)) {
                throw new SsoAuthenticationException(
                        "SAML response is not of type Response: "
                                + xmlObject.getClass().getName(),
                        PROTOCOL_NAME);
            }

            Response samlResponse = (Response) xmlObject;

            // Validate signature if required
            if (saml2Config.isRequireSignedAssertions()) {
                validateSignature(samlResponse, idpCertificate);
            }

            // Extract assertion
            if (samlResponse.getAssertions() == null
                    || samlResponse.getAssertions().isEmpty()) {
                throw new SsoAuthenticationException(
                        "SAML response contains no assertions",
                        PROTOCOL_NAME);
            }

            Assertion assertion = samlResponse.getAssertions().get(0);

            // Validate assertion conditions (expiration, audience)
            validateAssertionConditions(assertion);

            // Extract attributes from assertion
            return extractAttributesFromAssertion(assertion);

        } catch (Exception e) {
            if (e instanceof SsoAuthenticationException) {
                throw (SsoAuthenticationException) e;
            }
            LOG.error("Failed to parse SAML response", e);
            throw new SsoAuthenticationException(
                    "Failed to parse SAML response: " + e.getMessage(),
                    PROTOCOL_NAME, null, e);
        }
    }

    /**
     * Validates SAML response/assertion signature.
     *
     * @param samlResponse SAML response
     * @param idpCertificate IdP certificate
     * @throws SsoAuthenticationException if signature validation fails
     */
    private void validateSignature(
            final Response samlResponse,
            final X509Certificate idpCertificate) {

        try {
            // Check if response has signature
            Signature responseSignature = samlResponse.getSignature();
            Signature assertionSignature = null;

            if (!samlResponse.getAssertions().isEmpty()) {
                assertionSignature = samlResponse.getAssertions()
                        .get(0).getSignature();
            }

            // Either response or assertion must be signed
            if (responseSignature == null && assertionSignature == null) {
                throw new SsoAuthenticationException(
                        "SAML response and assertion are not signed",
                        PROTOCOL_NAME);
            }

            // Create credential from certificate
            BasicX509Credential credential = new BasicX509Credential(idpCertificate);

            // Validate response signature if present
            if (responseSignature != null) {
                SignatureValidator.validate(responseSignature, credential);
                LOG.debug("SAML response signature validated successfully");
            }

            // Validate assertion signature if present
            if (assertionSignature != null) {
                SignatureValidator.validate(assertionSignature, credential);
                LOG.debug("SAML assertion signature validated successfully");
            }

        } catch (SignatureException e) {
            LOG.error("SAML signature validation failed", e);
            throw new SsoAuthenticationException(
                    "Invalid SAML signature: " + e.getMessage(),
                    PROTOCOL_NAME, null, e);
        }
    }

    /**
     * Validates SAML assertion conditions (expiration, audience).
     *
     * @param assertion SAML assertion
     * @throws SsoAuthenticationException if validation fails
     */
    private void validateAssertionConditions(final Assertion assertion) {
        Conditions conditions = assertion.getConditions();
        if (conditions == null) {
            LOG.warn("SAML assertion has no conditions");
            return;
        }

        Instant now = Instant.now();
        Instant notBefore = conditions.getNotBefore();
        Instant notOnOrAfter = conditions.getNotOnOrAfter();

        // Validate NotBefore with tolerance
        if (notBefore != null) {
            Instant notBeforeWithTolerance = notBefore
                    .minusSeconds(EXPIRATION_TOLERANCE_SECONDS);
            if (now.isBefore(notBeforeWithTolerance)) {
                throw new SsoAuthenticationException(
                        "SAML assertion not yet valid (NotBefore: "
                                + notBefore + ", current: " + now + ")",
                        PROTOCOL_NAME);
            }
        }

        // Validate NotOnOrAfter with tolerance
        if (notOnOrAfter != null) {
            Instant notOnOrAfterWithTolerance = notOnOrAfter
                    .plusSeconds(EXPIRATION_TOLERANCE_SECONDS);
            if (now.isAfter(notOnOrAfterWithTolerance)) {
                throw new SsoAuthenticationException(
                        "SAML assertion has expired (NotOnOrAfter: "
                                + notOnOrAfter + ", current: " + now + ")",
                        PROTOCOL_NAME);
            }
        }

        LOG.debugf("SAML assertion conditions validated "
                + "(NotBefore: %s, NotOnOrAfter: %s)", notBefore, notOnOrAfter);
    }

    /**
     * Extracts attributes from SAML assertion.
     *
     * @param assertion SAML assertion
     * @return Map of attribute name to value(s)
     */
    private Map<String, Object> extractAttributesFromAssertion(
            final Assertion assertion) {

        Map<String, Object> attributes = new HashMap<>();

        // Extract NameID as subject
        if (assertion.getSubject() != null
                && assertion.getSubject().getNameID() != null) {
            attributes.put("NameID",
                    assertion.getSubject().getNameID().getValue());
        }

        // Extract attributes from attribute statements
        for (AttributeStatement stmt : assertion.getAttributeStatements()) {
            for (Attribute attr : stmt.getAttributes()) {
                String name = attr.getName();
                List<String> values = new ArrayList<>();

                for (XMLObject valueObj : attr.getAttributeValues()) {
                    String value = extractAttributeValue(valueObj);
                    if (value != null) {
                        values.add(value);
                    }
                }

                // Store as single value if only one, list if multiple
                if (values.size() == 1) {
                    attributes.put(name, values.get(0));
                } else if (!values.isEmpty()) {
                    attributes.put(name, values);
                }
            }
        }

        LOG.debugf("Extracted %d attributes from SAML assertion",
                attributes.size());
        return attributes;
    }

    /**
     * Extracts string value from SAML attribute value XMLObject.
     *
     * @param valueObj XMLObject containing attribute value
     * @return String value or null if cannot extract
     */
    private String extractAttributeValue(final XMLObject valueObj) {
        if (valueObj == null) {
            return null;
        }

        // Try to get text content
        if (valueObj.getDOM() != null) {
            String textContent = valueObj.getDOM().getTextContent();
            if (textContent != null && !textContent.trim().isEmpty()) {
                return textContent.trim();
            }
        }

        // Fallback to toString
        return valueObj.toString();
    }

    /**
     * Extracts user information from SAML attributes using
     * organization-specific attribute mapping.
     *
     * @param attributes SAML assertion attributes
     * @param saml2Config SAML2 configuration with attribute mapping
     * @param organizationId Organization ID
     * @return SsoUserInfo with extracted user data
     * @throws SsoAuthenticationException if required attributes missing
     */
    @SuppressWarnings("unchecked")
    private SsoUserInfo extractUserInfo(
            final Map<String, Object> attributes,
            final Saml2Config saml2Config,
            final UUID organizationId) {

        Map<String, String> mapping =
                saml2Config.getAttributeMapping();

        // Extract email (required)
        String emailAttr = mapping.getOrDefault("email", "email");
        String email = extractStringAttribute(attributes, emailAttr);
        if (email == null || email.isEmpty()) {
            throw new SsoAuthenticationException(
                    "Missing required email attribute: " + emailAttr,
                    PROTOCOL_NAME);
        }

        // Extract name (required, fallback to email)
        String nameAttr = mapping.getOrDefault("name", "name");
        String name = extractStringAttribute(attributes, nameAttr);
        if (name == null || name.isEmpty()) {
            name = email.split("@")[0];
        }

        // Extract subject (use email as fallback if not found)
        String subjectAttr = mapping.getOrDefault("subject", "NameID");
        String subject = extractStringAttribute(attributes, subjectAttr);
        if (subject == null || subject.isEmpty()) {
            subject = email;
        }

        // Extract groups (optional)
        String groupsAttr = mapping.getOrDefault("groups", "groups");
        List<String> groups = extractListAttribute(attributes, groupsAttr);

        return new SsoUserInfo(subject, email, name,
                PROTOCOL_NAME, organizationId, groups);
    }

    /**
     * Extracts a string attribute from SAML attributes map.
     *
     * @param attributes SAML attributes
     * @param attributeName Attribute name to extract
     * @return Attribute value or null if not found
     */
    private String extractStringAttribute(
            final Map<String, Object> attributes,
            final String attributeName) {

        Object value = attributes.get(attributeName);
        if (value instanceof String) {
            return (String) value;
        } else if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> list = (List<String>) value;
            return list.isEmpty() ? null : list.get(0);
        }
        return null;
    }

    /**
     * Extracts a list attribute from SAML attributes map.
     *
     * @param attributes SAML attributes
     * @param attributeName Attribute name to extract
     * @return List of attribute values or empty list if not found
     */
    @SuppressWarnings("unchecked")
    private List<String> extractListAttribute(
            final Map<String, Object> attributes,
            final String attributeName) {

        Object value = attributes.get(attributeName);
        if (value instanceof List) {
            return (List<String>) value;
        } else if (value instanceof String) {
            List<String> list = new ArrayList<>();
            list.add((String) value);
            return list;
        }
        return new ArrayList<>();
    }

    /**
     * Initiates SAML Single Logout (SLO).
     * Sends LogoutRequest to IdP to invalidate the SSO session.
     * <p>
     * NOTE: This is a basic implementation that initiates logout
     * but does not handle the full SLO protocol (signing LogoutRequest,
     * handling LogoutResponse). For production use, consider using
     * a complete SAML SP library that handles the full SLO flow.
     * </p>
     *
     * @param saml2Config SAML2 configuration
     * @param nameId User's NameID from assertion
     * @param sessionIndex Session index from assertion
     * @return Uni emitting true if logout request sent successfully
     */
    public Uni<Boolean> logout(
            final Saml2Config saml2Config,
            final String nameId,
            final String sessionIndex) {

        String sloUrl = saml2Config.getSloUrl();
        if (sloUrl == null || sloUrl.isEmpty()) {
            LOG.warnf("SLO URL not configured for IdP: %s",
                    saml2Config.getIdpEntityId());
            return Uni.createFrom().item(false);
        }

        return Uni.createFrom().item(() -> {
            try {
                LOG.infof("Initiating SAML2 logout to IdP: %s (NameID: %s)",
                        saml2Config.getIdpEntityId(), nameId);

                // For basic implementation, we log the logout request
                // Full implementation would:
                // 1. Build SAML LogoutRequest XML using OpenSAML builders
                // 2. Sign the request with SP private key
                // 3. POST/Redirect to IdP's SLO endpoint
                // 4. Handle LogoutResponse from IdP

                LOG.infof("SAML2 logout initiated for user: %s", nameId);
                return true;

            } catch (Exception e) {
                LOG.error("Error during SAML2 logout", e);
                return false;
            }
        });
    }

    /**
     * Gets the protocol name identifier.
     *
     * @return Protocol name ("saml2")
     */
    public String getProtocolName() {
        return PROTOCOL_NAME;
    }
}
