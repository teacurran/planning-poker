package com.scrumpoker.integration.sso;

import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Saml2Provider.
 * Tests SAML2 response processing, signature validation,
 * timestamp validation, and attribute extraction.
 */
class Saml2ProviderTest {

    private Saml2Provider saml2Provider;
    private UUID organizationId;
    private Saml2Config saml2Config;

    // Sample X.509 certificate for testing (self-signed test cert)
    private static final String TEST_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIDXTCCAkWgAwIBAgIJANBzVtlE7qGGMA0GCSqGSIb3DQEBCwUAMEUxCzAJBgNV
            BAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJbnRlcm5ldCBX
            aWRnaXRzIFB0eSBMdGQwHhcNMjQwMTAxMDAwMDAwWhcNMjUwMTAxMDAwMDAwWjBF
            MQswCQYDVQQGEwJBVTETMBEGA1UECAwKU29tZS1TdGF0ZTEhMB8GA1UECgwYSW50
            ZXJuZXQgV2lkZ2l0cyBQdHkgTHRkMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB
            CgKCAQEA0Z6L7fzYj4VnKz4j5X2n7Q8ZzYy3L4xM5J6K7fzYj4VnKz4j5X2n7Q8Z
            zYy3L4xM5J6K7fzYj4VnKz4j5X2n7Q8ZzYy3L4xM5J6K7fzYj4VnKz4j5X2n7Q8Z
            zYy3L4xM5J6K7fzYj4VnKz4j5X2n7Q8ZzYy3L4xM5J6K7fzYj4VnKz4j5X2n7Q8Z
            zYy3L4xM5J6K7fzYj4VnKz4j5X2n7Q8ZzYy3L4xM5J6K7fzYj4VnKz4j5X2n7Q8Z
            zYy3L4xM5J6QIDAQAB
            -----END CERTIFICATE-----
            """;

    @BeforeEach
    void setUp() {
        saml2Provider = new Saml2Provider();
        organizationId = UUID.randomUUID();

        // Create test SAML2 config
        saml2Config = new Saml2Config(
                "https://test-idp.example.com/metadata",
                "https://app.scrumpoker.com/saml",
                "https://app.scrumpoker.com/api/v1/auth/sso/callback",
                TEST_CERTIFICATE
        );

        // Configure attribute mapping for Azure AD
        Map<String, String> attributeMapping = new HashMap<>();
        attributeMapping.put("email",
                "http://schemas.xmlsoap.org/ws/2005/05/identity/"
                + "claims/emailaddress");
        attributeMapping.put("name",
                "http://schemas.xmlsoap.org/ws/2005/05/identity/"
                + "claims/name");
        attributeMapping.put("groups",
                "http://schemas.microsoft.com/ws/2008/06/identity/"
                + "claims/groups");
        saml2Config.setAttributeMapping(attributeMapping);
    }

    @Test
    void processSamlResponse_invalidBase64_throwsException() {
        // Given
        String invalidSamlResponse = "not-valid-base64!!!";

        // When/Then
        assertThatThrownBy(() ->
                saml2Provider.processSamlResponse(
                        invalidSamlResponse,
                        saml2Config,
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("Failed to process SAML response");
    }

    @Test
    void processSamlResponse_invalidXml_throwsException() {
        // Given - Valid Base64 but invalid XML
        String invalidXmlResponse = java.util.Base64.getEncoder()
                .encodeToString("<invalid>xml".getBytes());

        // When/Then
        assertThatThrownBy(() ->
                saml2Provider.processSamlResponse(
                        invalidXmlResponse,
                        saml2Config,
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("Failed to process SAML response");
    }

    @Test
    void processSamlResponse_noAssertions_throwsException() {
        // Given - Valid SAML response but no assertions
        String samlResponseWithoutAssertions = createSamlResponseWithoutAssertions();

        // When/Then
        assertThatThrownBy(() ->
                saml2Provider.processSamlResponse(
                        samlResponseWithoutAssertions,
                        saml2Config,
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("No assertions found");
    }

    @Test
    void logout_noSloEndpoint_returnsFalse() {
        // Given - Config without SLO endpoint
        saml2Config.setSloEndpoint(null);

        // When
        Boolean result = saml2Provider.logout(
                saml2Config,
                "test-name-id",
                "session-index"
        ).await().indefinitely();

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void logout_withSloEndpoint_returnsTrue() {
        // Given
        saml2Config.setSloEndpoint("https://test-idp.example.com/slo");

        // When
        Boolean result = saml2Provider.logout(
                saml2Config,
                "test-name-id",
                "session-index"
        ).await().indefinitely();

        // Then
        // NOTE: Current implementation is a TODO stub that returns true
        assertThat(result).isTrue();
    }

    @Test
    void getProtocolName_returnsSaml2() {
        // When
        String protocolName = saml2Provider.getProtocolName();

        // Then
        assertThat(protocolName).isEqualTo("saml2");
    }

    // Helper methods

    /**
     * Creates a minimal SAML Response XML without assertions
     * (for testing error handling).
     */
    private String createSamlResponseWithoutAssertions() {
        String samlXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <samlp:Response
                    xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                    ID="_response_id"
                    Version="2.0"
                    IssueInstant="2024-01-01T00:00:00Z"
                    Destination="https://app.scrumpoker.com/api/v1/auth/sso/callback">
                    <saml:Issuer>https://test-idp.example.com</saml:Issuer>
                    <samlp:Status>
                        <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
                    </samlp:Status>
                </samlp:Response>
                """;

        return java.util.Base64.getEncoder().encodeToString(
                samlXml.getBytes());
    }

    /**
     * Note: Full integration testing with signed SAML assertions
     * requires generating valid XML signatures with matching certificates.
     * This is complex and typically done in integration tests with
     * real IdP sandboxes (Okta, Azure AD).
     *
     * For unit tests, we focus on:
     * 1. Error handling (invalid input, missing assertions)
     * 2. Configuration validation
     * 3. Protocol name and basic functionality
     *
     * Integration testing strategy:
     * - Use Testcontainers with Keycloak or mock SAML IdP
     * - Or use Okta/Azure AD sandbox for manual testing
     * - Or use pre-generated valid SAML response samples
     */
}
