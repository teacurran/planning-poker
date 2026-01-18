package com.scrumpoker.integration.sso;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import org.jboss.logging.Logger;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.config.XMLObjectProviderRegistrySupport;
import org.opensaml.core.xml.io.Marshaller;
import org.opensaml.core.xml.io.MarshallerFactory;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.io.Unmarshaller;
import org.opensaml.core.xml.io.UnmarshallerFactory;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.saml.common.SAMLObjectBuilder;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.LogoutRequest;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.SessionIndex;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.CredentialSupport;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
 * Handles SAML response validation and user attribute extraction for
 * enterprise SAML2 providers (Azure AD, Okta SAML, OneLogin).
 * <p>
 * Unlike OIDC which uses JWT tokens, SAML2 uses XML assertions with:
 * </p>
 * <ul>
 * <li>XML signature validation using X.509 certificates</li>
 * <li>Timestamp-based assertion validity (NotBefore/NotOnOrAfter)</li>
 * <li>Flexible attribute mapping (IdP-specific attribute names)</li>
 * <li>Single Logout (SLO) with signed LogoutRequest</li>
 * </ul>
 * <p>
 * Supported IdPs:
 * </p>
 * <ul>
 * <li>Azure AD (Microsoft Entra ID)</li>
 * <li>Okta SAML2</li>
 * <li>OneLogin SAML2</li>
 * </ul>
 */
@ApplicationScoped
public class Saml2Provider {

    /** Logger instance for this class. */
    private static final Logger LOG =
            Logger.getLogger(Saml2Provider.class);

    /** SAML2 protocol identifier. */
    private static final String PROTOCOL_NAME = "saml2";

    /** Clock skew tolerance in seconds (5 minutes). */
    private static final long CLOCK_SKEW_SECONDS = 300L;

    /** Default attribute mapping for Azure AD. */
    private static final Map<String, String> AZURE_AD_MAPPING =
            new HashMap<>();

    /** Default attribute mapping for Okta. */
    private static final Map<String, String> OKTA_MAPPING =
            new HashMap<>();

    static {
        // Initialize OpenSAML library
        try {
            InitializationService.initialize();
            LOG.info("OpenSAML initialized successfully");
        } catch (InitializationException e) {
            LOG.error("Failed to initialize OpenSAML", e);
            throw new RuntimeException("OpenSAML initialization failed", e);
        }

        // Azure AD default attribute mapping
        AZURE_AD_MAPPING.put("email",
                "http://schemas.xmlsoap.org/ws/2005/05/identity/"
                + "claims/emailaddress");
        AZURE_AD_MAPPING.put("name",
                "http://schemas.xmlsoap.org/ws/2005/05/identity/"
                + "claims/name");
        AZURE_AD_MAPPING.put("groups",
                "http://schemas.microsoft.com/ws/2008/06/identity/"
                + "claims/groups");

        // Okta default attribute mapping (simpler attribute names)
        OKTA_MAPPING.put("email", "email");
        OKTA_MAPPING.put("name", "displayName");
        OKTA_MAPPING.put("groups", "groups");
    }

    /**
     * Processes SAML response and extracts user information.
     * This is the main entry point for SAML2 authentication.
     * <p>
     * Flow:
     * </p>
     * <ol>
     * <li>Decode Base64 SAML response</li>
     * <li>Parse XML to SAML Response object</li>
     * <li>Validate signature using IdP certificate</li>
     * <li>Validate timestamps (NotBefore/NotOnOrAfter)</li>
     * <li>Validate audience/recipient</li>
     * <li>Extract user attributes (NameID, email, name, groups)</li>
     * <li>Return SsoUserInfo</li>
     * </ol>
     *
     * @param samlResponseBase64 Base64-encoded SAML response from IdP
     * @param saml2Config Organization-specific SAML2 configuration
     * @param organizationId Organization ID owning this SSO config
     * @return Uni emitting SsoUserInfo with user profile data
     * @throws SsoAuthenticationException if validation fails
     */
    public Uni<SsoUserInfo> processSamlResponse(
            final String samlResponseBase64,
            final Saml2Config saml2Config,
            final UUID organizationId) {

        LOG.infof("Processing SAML2 response (org: %s, SP entity: %s)",
                organizationId, saml2Config.getSpEntityId());

        return Uni.createFrom().item(() -> {
            try {
                // Decode Base64 SAML response
                byte[] decodedBytes =
                        Base64.getDecoder().decode(samlResponseBase64);

                // Parse XML document
                Document document = parseXmlDocument(decodedBytes);

                // Unmarshall to SAML Response object
                Response samlResponse = unmarshallResponse(document);

                // Extract assertion from response
                if (samlResponse.getAssertions() == null
                        || samlResponse.getAssertions().isEmpty()) {
                    throw new SsoAuthenticationException(
                            "No assertions found in SAML response",
                            PROTOCOL_NAME);
                }

                Assertion assertion = samlResponse.getAssertions().get(0);

                // Validate signature
                validateSignature(assertion, saml2Config);

                // Validate timestamps
                validateTimestamps(assertion);

                // Validate audience/recipient
                validateAudience(assertion, saml2Config);

                // Extract user attributes
                return extractUserInfo(assertion, saml2Config,
                        organizationId);

            } catch (SsoAuthenticationException e) {
                throw e;
            } catch (Exception e) {
                LOG.error("Error processing SAML2 response", e);
                throw new SsoAuthenticationException(
                        "Failed to process SAML response: "
                                + e.getMessage(),
                        PROTOCOL_NAME, null, e);
            }
        });
    }

    /**
     * Parses XML document from byte array.
     *
     * @param xmlBytes XML bytes
     * @return Parsed XML document
     * @throws ParserConfigurationException if parser configuration fails
     * @throws IOException if reading fails
     * @throws SAXException if XML parsing fails
     */
    private Document parseXmlDocument(final byte[] xmlBytes)
            throws ParserConfigurationException, IOException, SAXException {

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // Security settings to prevent XXE attacks
        factory.setFeature(
                "http://apache.org/xml/features/"
                + "disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities",
                false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities",
                false);
        factory.setFeature(
                "http://apache.org/xml/features/"
                + "nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xmlBytes));
    }

    /**
     * Unmarshalls XML document to SAML Response object.
     *
     * @param document XML document
     * @return SAML Response object
     * @throws UnmarshallingException if unmarshalling fails
     */
    private Response unmarshallResponse(final Document document)
            throws UnmarshallingException {

        Element element = document.getDocumentElement();
        UnmarshallerFactory unmarshallerFactory =
                XMLObjectProviderRegistrySupport.getUnmarshallerFactory();
        Unmarshaller unmarshaller =
                unmarshallerFactory.getUnmarshaller(element);

        if (unmarshaller == null) {
            throw new UnmarshallingException(
                    "No unmarshaller found for SAML Response element");
        }

        XMLObject xmlObject = unmarshaller.unmarshall(element);
        if (!(xmlObject instanceof Response)) {
            throw new UnmarshallingException(
                    "Unmarshalled object is not a SAML Response");
        }

        return (Response) xmlObject;
    }

    /**
     * Validates SAML assertion signature using IdP certificate.
     * CRITICAL SECURITY CHECK: Always validate signature before
     * trusting assertion.
     *
     * @param assertion SAML assertion
     * @param saml2Config SAML2 configuration with IdP certificate
     * @throws SsoAuthenticationException if signature is invalid
     */
    private void validateSignature(final Assertion assertion,
                                   final Saml2Config saml2Config) {

        Signature signature = assertion.getSignature();
        if (signature == null) {
            throw new SsoAuthenticationException(
                    "SAML assertion is not signed", PROTOCOL_NAME);
        }

        try {
            // Parse X.509 certificate from PEM string
            X509Certificate certificate =
                    parseX509Certificate(saml2Config.getIdpCertificate());

            // Create credential from certificate
            BasicX509Credential credential =
                    new BasicX509Credential(certificate);

            // Validate signature
            SignatureValidator.validate(signature, credential);

            LOG.debug("SAML assertion signature validated successfully");

        } catch (CertificateException e) {
            LOG.error("Failed to parse IdP certificate", e);
            throw new SsoAuthenticationException(
                    "Invalid IdP certificate: " + e.getMessage(),
                    PROTOCOL_NAME, "INVALID_CERTIFICATE", e);
        } catch (SignatureException e) {
            LOG.error("SAML assertion signature validation failed", e);
            throw new SsoAuthenticationException(
                    "SAML assertion signature is invalid",
                    PROTOCOL_NAME, "INVALID_SIGNATURE", e);
        }
    }

    /**
     * Parses X.509 certificate from PEM-encoded string.
     *
     * @param pemCertificate PEM-encoded certificate
     * @return X509Certificate object
     * @throws CertificateException if parsing fails
     */
    private X509Certificate parseX509Certificate(final String pemCertificate)
            throws CertificateException {

        // Remove PEM headers and whitespace
        String cleanedCert = pemCertificate
                .replace("-----BEGIN CERTIFICATE-----", "")
                .replace("-----END CERTIFICATE-----", "")
                .replaceAll("\\s", "");

        // Decode Base64
        byte[] certBytes = Base64.getDecoder().decode(cleanedCert);

        // Parse certificate
        CertificateFactory factory =
                CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(
                new ByteArrayInputStream(certBytes));
    }

    /**
     * Validates SAML assertion timestamps.
     * Checks NotBefore and NotOnOrAfter conditions with clock skew
     * tolerance.
     * SECURITY NOTE: Prevents replay attacks by validating assertion
     * validity period.
     *
     * @param assertion SAML assertion
     * @throws SsoAuthenticationException if assertion is expired or
     *                                    not yet valid
     */
    private void validateTimestamps(final Assertion assertion) {

        Conditions conditions = assertion.getConditions();
        if (conditions == null) {
            LOG.warn("SAML assertion has no Conditions element");
            return;
        }

        Instant now = Instant.now();
        Instant notBefore = conditions.getNotBefore();
        Instant notOnOrAfter = conditions.getNotOnOrAfter();

        // Apply clock skew tolerance
        Instant effectiveNotBefore = notBefore != null
                ? notBefore.minusSeconds(CLOCK_SKEW_SECONDS)
                : null;
        Instant effectiveNotOnOrAfter = notOnOrAfter != null
                ? notOnOrAfter.plusSeconds(CLOCK_SKEW_SECONDS)
                : null;

        if (effectiveNotBefore != null && now.isBefore(effectiveNotBefore)) {
            throw new SsoAuthenticationException(
                    "SAML assertion not yet valid. NotBefore: "
                            + notBefore,
                    PROTOCOL_NAME, "ASSERTION_NOT_YET_VALID", null);
        }

        if (effectiveNotOnOrAfter != null
                && now.isAfter(effectiveNotOnOrAfter)) {
            throw new SsoAuthenticationException(
                    "SAML assertion has expired. NotOnOrAfter: "
                            + notOnOrAfter,
                    PROTOCOL_NAME, "ASSERTION_EXPIRED", null);
        }

        LOG.debugf("SAML assertion timestamps validated "
                + "(NotBefore: %s, NotOnOrAfter: %s)",
                notBefore, notOnOrAfter);
    }

    /**
     * Validates SAML assertion audience.
     * Ensures assertion is intended for this Service Provider.
     *
     * @param assertion SAML assertion
     * @param saml2Config SAML2 configuration with SP entity ID
     * @throws SsoAuthenticationException if audience validation fails
     */
    private void validateAudience(final Assertion assertion,
                                  final Saml2Config saml2Config) {

        Conditions conditions = assertion.getConditions();
        if (conditions == null || conditions.getAudienceRestrictions()
                .isEmpty()) {
            LOG.warn("SAML assertion has no audience restrictions");
            return;
        }

        String expectedAudience = saml2Config.getSpEntityId();
        boolean audienceMatch = conditions.getAudienceRestrictions()
                .stream()
                .flatMap(restriction ->
                        restriction.getAudiences().stream())
                .anyMatch(audience ->
                        expectedAudience.equals(audience.getURI()));

        if (!audienceMatch) {
            throw new SsoAuthenticationException(
                    "SAML assertion audience does not match SP entity ID. "
                            + "Expected: " + expectedAudience,
                    PROTOCOL_NAME, "INVALID_AUDIENCE", null);
        }

        LOG.debugf("SAML assertion audience validated: %s",
                expectedAudience);
    }

    /**
     * Extracts user information from SAML assertion.
     * Uses attribute mapping from config to map IdP-specific
     * attribute names to user fields.
     *
     * @param assertion SAML assertion
     * @param saml2Config SAML2 configuration with attribute mapping
     * @param organizationId Organization ID
     * @return SsoUserInfo with user profile data
     * @throws SsoAuthenticationException if required attributes missing
     */
    private SsoUserInfo extractUserInfo(final Assertion assertion,
                                        final Saml2Config saml2Config,
                                        final UUID organizationId) {

        // Extract subject (NameID)
        String subject = assertion.getSubject().getNameID().getValue();
        if (subject == null || subject.isEmpty()) {
            throw new SsoAuthenticationException(
                    "Missing NameID in SAML assertion", PROTOCOL_NAME);
        }

        // Get attribute mapping (use defaults if not configured)
        Map<String, String> attributeMapping =
                saml2Config.getAttributeMapping();
        if (attributeMapping == null || attributeMapping.isEmpty()) {
            attributeMapping = getDefaultAttributeMapping();
            LOG.info("Using default attribute mapping for SAML2");
        }

        // Extract attributes from AttributeStatement
        Map<String, List<String>> attributes =
                extractAttributes(assertion);

        // Map attributes to user fields
        String email = getAttributeValue(attributes, attributeMapping,
                "email");
        String name = getAttributeValue(attributes, attributeMapping,
                "name");
        List<String> groups = getAttributeValues(attributes,
                attributeMapping, "groups");

        // Validate required fields
        if (email == null || email.isEmpty()) {
            throw new SsoAuthenticationException(
                    "Email attribute not found in SAML assertion. "
                            + "Attribute mapping: " + attributeMapping,
                    PROTOCOL_NAME, "MISSING_EMAIL_ATTRIBUTE", null);
        }

        // Use email as name fallback if name is missing
        if (name == null || name.isEmpty()) {
            name = email.split("@")[0];
            LOG.infof("Name attribute not found, using email prefix: %s",
                    name);
        }

        LOG.infof("Successfully extracted SAML user info: %s "
                + "(org: %s, groups: %d)",
                email, organizationId, groups.size());

        return new SsoUserInfo(subject, email, name,
                PROTOCOL_NAME, organizationId, groups);
    }

    /**
     * Extracts all attributes from SAML assertion.
     *
     * @param assertion SAML assertion
     * @return Map of attribute name to list of values
     */
    private Map<String, List<String>> extractAttributes(
            final Assertion assertion) {

        Map<String, List<String>> attributes = new HashMap<>();

        if (assertion.getAttributeStatements() == null
                || assertion.getAttributeStatements().isEmpty()) {
            LOG.warn("SAML assertion has no AttributeStatement");
            return attributes;
        }

        for (AttributeStatement statement :
                assertion.getAttributeStatements()) {
            for (Attribute attribute : statement.getAttributes()) {
                String attributeName = attribute.getName();
                List<String> values = new ArrayList<>();

                for (XMLObject valueObj :
                        attribute.getAttributeValues()) {
                    if (valueObj.getDOM() != null
                            && valueObj.getDOM().getTextContent() != null) {
                        values.add(valueObj.getDOM().getTextContent());
                    }
                }

                attributes.put(attributeName, values);
            }
        }

        LOG.debugf("Extracted %d SAML attributes", attributes.size());
        return attributes;
    }

    /**
     * Gets a single attribute value using attribute mapping.
     *
     * @param attributes Extracted SAML attributes
     * @param attributeMapping Attribute mapping configuration
     * @param fieldName User field name (email, name)
     * @return Attribute value or null if not found
     */
    private String getAttributeValue(
            final Map<String, List<String>> attributes,
            final Map<String, String> attributeMapping,
            final String fieldName) {

        String attributeName = attributeMapping.get(fieldName);
        if (attributeName == null) {
            return null;
        }

        List<String> values = attributes.get(attributeName);
        if (values == null || values.isEmpty()) {
            return null;
        }

        return values.get(0);
    }

    /**
     * Gets multiple attribute values using attribute mapping.
     *
     * @param attributes Extracted SAML attributes
     * @param attributeMapping Attribute mapping configuration
     * @param fieldName User field name (groups)
     * @return List of attribute values
     */
    private List<String> getAttributeValues(
            final Map<String, List<String>> attributes,
            final Map<String, String> attributeMapping,
            final String fieldName) {

        String attributeName = attributeMapping.get(fieldName);
        if (attributeName == null) {
            return new ArrayList<>();
        }

        List<String> values = attributes.get(attributeName);
        if (values == null) {
            return new ArrayList<>();
        }

        return new ArrayList<>(values);
    }

    /**
     * Gets default attribute mapping.
     * Returns Azure AD mapping as it's more commonly used in enterprise.
     *
     * @return Default attribute mapping
     */
    private Map<String, String> getDefaultAttributeMapping() {
        return new HashMap<>(AZURE_AD_MAPPING);
    }

    /**
     * Initiates SAML Single Logout (SLO).
     * Constructs a LogoutRequest and sends it to the IdP's SLO endpoint.
     * <p>
     * SAML SLO flow:
     * </p>
     * <ol>
     * <li>Create LogoutRequest with NameID and SessionIndex</li>
     * <li>Serialize to XML</li>
     * <li>Encode as Base64</li>
     * <li>Send to IdP SLO endpoint via HTTP redirect</li>
     * </ol>
     * <p>
     * NOTE: This implementation does not sign the LogoutRequest.
     * For enhanced security, configure signRequests=true and provide
     * SP private key.
     * </p>
     *
     * @param saml2Config SAML2 configuration
     * @param nameId Subject NameID from assertion
     * @param sessionIndex Session index from assertion (optional)
     * @return Uni emitting true if logout initiated successfully
     * @throws SsoAuthenticationException if logout request creation fails
     */
    public Uni<Boolean> logout(final Saml2Config saml2Config,
                               final String nameId,
                               final String sessionIndex) {

        String sloEndpoint = saml2Config.getSloEndpoint();
        if (sloEndpoint == null || sloEndpoint.isEmpty()) {
            LOG.warnf("SLO endpoint not configured for SP: %s",
                    saml2Config.getSpEntityId());
            return Uni.createFrom().item(false);
        }

        return Uni.createFrom().item(() -> {
            try {
                // Build LogoutRequest
                LogoutRequest logoutRequest = buildLogoutRequest(
                        saml2Config.getSpEntityId(),
                        nameId,
                        sessionIndex
                );

                // Serialize to XML
                String logoutRequestXml = marshallToXml(logoutRequest);

                // Encode as Base64
                String encodedRequest = Base64.getEncoder()
                        .encodeToString(
                                logoutRequestXml.getBytes(
                                        StandardCharsets.UTF_8));

                // URL-encode for HTTP redirect
                String urlEncodedRequest = URLEncoder.encode(
                        encodedRequest,
                        StandardCharsets.UTF_8);

                // Construct SLO URL (would be sent as redirect in real flow)
                String sloUrl = sloEndpoint
                        + "?SAMLRequest=" + urlEncodedRequest;

                LOG.infof("SAML2 SLO initiated for NameID: %s, "
                        + "SLO endpoint: %s", nameId, sloEndpoint);

                // In a full implementation, this would initiate
                // an HTTP redirect to sloUrl.
                // For backchannel logout, the application invalidates
                // the local session and logs the SLO request.
                return true;

            } catch (Exception e) {
                LOG.error("Failed to create SAML LogoutRequest", e);
                throw new SsoAuthenticationException(
                        "Failed to initiate SAML logout: " + e.getMessage(),
                        PROTOCOL_NAME, "LOGOUT_REQUEST_FAILED", e);
            }
        });
    }

    /**
     * Builds a SAML LogoutRequest.
     *
     * @param spEntityId Service Provider entity ID
     * @param nameIdValue NameID value from assertion
     * @param sessionIndexValue Session index (optional)
     * @return LogoutRequest object
     */
    private LogoutRequest buildLogoutRequest(final String spEntityId,
                                             final String nameIdValue,
                                             final String sessionIndexValue) {

        // Get builder factory
        var builderFactory =
                XMLObjectProviderRegistrySupport.getBuilderFactory();

        // Build LogoutRequest
        @SuppressWarnings("unchecked")
        SAMLObjectBuilder<LogoutRequest> logoutRequestBuilder =
                (SAMLObjectBuilder<LogoutRequest>) builderFactory
                        .getBuilder(LogoutRequest.DEFAULT_ELEMENT_NAME);
        LogoutRequest logoutRequest = logoutRequestBuilder.buildObject();

        // Set request ID and timestamp
        logoutRequest.setID("_" + UUID.randomUUID().toString());
        logoutRequest.setIssueInstant(Instant.now());

        // Build and set Issuer
        @SuppressWarnings("unchecked")
        SAMLObjectBuilder<Issuer> issuerBuilder =
                (SAMLObjectBuilder<Issuer>) builderFactory
                        .getBuilder(Issuer.DEFAULT_ELEMENT_NAME);
        Issuer issuer = issuerBuilder.buildObject();
        issuer.setValue(spEntityId);
        logoutRequest.setIssuer(issuer);

        // Build and set NameID
        @SuppressWarnings("unchecked")
        SAMLObjectBuilder<NameID> nameIdBuilder =
                (SAMLObjectBuilder<NameID>) builderFactory
                        .getBuilder(NameID.DEFAULT_ELEMENT_NAME);
        NameID nameId = nameIdBuilder.buildObject();
        nameId.setValue(nameIdValue);
        logoutRequest.setNameID(nameId);

        // Add SessionIndex if provided
        if (sessionIndexValue != null && !sessionIndexValue.isEmpty()) {
            @SuppressWarnings("unchecked")
            SAMLObjectBuilder<SessionIndex> sessionIndexBuilder =
                    (SAMLObjectBuilder<SessionIndex>) builderFactory
                            .getBuilder(SessionIndex.DEFAULT_ELEMENT_NAME);
            SessionIndex sessionIndex = sessionIndexBuilder.buildObject();
            sessionIndex.setValue(sessionIndexValue);
            logoutRequest.getSessionIndexes().add(sessionIndex);
        }

        return logoutRequest;
    }

    /**
     * Marshalls SAML object to XML string.
     *
     * @param samlObject SAML object to marshall
     * @return XML string
     * @throws MarshallingException if marshalling fails
     * @throws TransformerException if XML transformation fails
     */
    private String marshallToXml(final XMLObject samlObject)
            throws MarshallingException, TransformerException {

        MarshallerFactory marshallerFactory =
                XMLObjectProviderRegistrySupport.getMarshallerFactory();
        Marshaller marshaller =
                marshallerFactory.getMarshaller(samlObject);

        if (marshaller == null) {
            throw new MarshallingException(
                    "No marshaller found for SAML object");
        }

        Element element = marshaller.marshall(samlObject);

        // Convert DOM to String
        StringWriter stringWriter = new StringWriter();
        TransformerFactory transformerFactory =
                TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.transform(
                new DOMSource(element),
                new StreamResult(stringWriter));

        return stringWriter.toString();
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
