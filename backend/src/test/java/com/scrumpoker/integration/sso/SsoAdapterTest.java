package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link SsoAdapter}.
 * Tests SSO adapter routing and configuration parsing.
 */
@ExtendWith(MockitoExtension.class)
class SsoAdapterTest {

    @Mock
    private OidcProvider oidcProvider;

    @Mock
    private Saml2Provider saml2Provider;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SsoAdapter ssoAdapter;

    private UUID organizationId;
    private SsoAdapter.SsoAuthParams authParams;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        authParams = new SsoAdapter.SsoAuthParams(
                "test-code-verifier",
                "https://app.scrumpoker.com/auth/callback"
        );
    }

    @Test
    void authenticate_oidcProtocol_routesToOidcProvider() throws JsonProcessingException {
        // Given
        String ssoConfigJson = createOidcConfigJson();
        SsoConfig ssoConfig = createOidcSsoConfig();
        String authCode = "test-authorization-code";

        SsoUserInfo expectedUserInfo = new SsoUserInfo(
                "test-subject",
                "test@example.com",
                "Test User",
                "oidc",
                organizationId,
                Arrays.asList("Admin", "Users")
        );

        when(objectMapper.readValue(ssoConfigJson, SsoConfig.class))
                .thenReturn(ssoConfig);
        when(oidcProvider.exchangeCodeForToken(
                eq(authCode),
                eq(authParams.getCodeVerifier()),
                eq(authParams.getRedirectUri()),
                any(OidcConfig.class),
                eq(organizationId)
        )).thenReturn(Uni.createFrom().item(expectedUserInfo));

        // When
        SsoUserInfo result = ssoAdapter.authenticate(
                ssoConfigJson,
                authCode,
                authParams,
                organizationId
        ).await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getProtocol()).isEqualTo("oidc");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(oidcProvider).exchangeCodeForToken(
                eq(authCode),
                eq(authParams.getCodeVerifier()),
                eq(authParams.getRedirectUri()),
                any(OidcConfig.class),
                eq(organizationId)
        );
    }

    @Test
    void authenticate_saml2Protocol_routesToSaml2Provider() throws JsonProcessingException {
        // Given
        String ssoConfigJson = createSaml2ConfigJson();
        SsoConfig ssoConfig = createSaml2SsoConfig();
        String samlResponse = "base64-encoded-saml-response";

        SsoUserInfo expectedUserInfo = new SsoUserInfo(
                "test-subject",
                "test@example.com",
                "Test User",
                "saml2",
                organizationId
        );

        when(objectMapper.readValue(ssoConfigJson, SsoConfig.class))
                .thenReturn(ssoConfig);
        when(saml2Provider.validateAssertion(
                eq(samlResponse),
                any(Saml2Config.class),
                eq(organizationId)
        )).thenReturn(Uni.createFrom().item(expectedUserInfo));

        // When
        SsoUserInfo result = ssoAdapter.authenticate(
                ssoConfigJson,
                samlResponse,
                null,  // SAML2 doesn't need authParams
                organizationId
        ).await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getProtocol()).isEqualTo("saml2");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(saml2Provider).validateAssertion(
                eq(samlResponse),
                any(Saml2Config.class),
                eq(organizationId)
        );
    }

    @Test
    void authenticate_unsupportedProtocol_throwsException() throws JsonProcessingException {
        // Given
        String ssoConfigJson = """
                {
                    "protocol": "unsupported-protocol",
                    "oidc": null,
                    "saml2": null
                }
                """;

        SsoConfig ssoConfig = new SsoConfig();
        ssoConfig.setProtocol("unsupported-protocol");

        when(objectMapper.readValue(ssoConfigJson, SsoConfig.class))
                .thenReturn(ssoConfig);

        // When/Then
        assertThatThrownBy(() ->
                ssoAdapter.authenticate(
                        ssoConfigJson,
                        "auth-data",
                        authParams,
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("Unsupported SSO protocol");
    }

    @Test
    void authenticate_nullSsoConfig_throwsException() {
        // When/Then
        assertThatThrownBy(() ->
                ssoAdapter.authenticate(
                        null,
                        "auth-data",
                        authParams,
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SSO configuration cannot be null");
    }

    @Test
    void authenticate_nullAuthenticationData_throwsException() throws JsonProcessingException {
        // Given
        String ssoConfigJson = createOidcConfigJson();
        lenient().when(objectMapper.readValue(anyString(), eq(SsoConfig.class)))
                .thenReturn(createOidcSsoConfig());

        // When/Then
        assertThatThrownBy(() ->
                ssoAdapter.authenticate(
                        ssoConfigJson,
                        null,
                        authParams,
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authentication data cannot be null");
    }

    @Test
    void authenticate_nullOrganizationId_throwsException() throws JsonProcessingException {
        // Given
        String ssoConfigJson = createOidcConfigJson();
        lenient().when(objectMapper.readValue(anyString(), eq(SsoConfig.class)))
                .thenReturn(createOidcSsoConfig());

        // When/Then
        assertThatThrownBy(() ->
                ssoAdapter.authenticate(
                        ssoConfigJson,
                        "auth-data",
                        authParams,
                        null
                ).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Organization ID cannot be null");
    }

    @Test
    void authenticate_oidcMissingAuthParams_throwsException() throws JsonProcessingException {
        // Given
        String ssoConfigJson = createOidcConfigJson();
        SsoConfig ssoConfig = createOidcSsoConfig();

        when(objectMapper.readValue(ssoConfigJson, SsoConfig.class))
                .thenReturn(ssoConfig);

        // When/Then
        assertThatThrownBy(() ->
                ssoAdapter.authenticate(
                        ssoConfigJson,
                        "auth-code",
                        null,  // OIDC requires authParams
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OIDC authentication requires");
    }

    @Test
    void authenticate_invalidJsonConfig_throwsException() throws JsonProcessingException {
        // Given
        String invalidJson = "{ invalid json }";

        when(objectMapper.readValue(invalidJson, SsoConfig.class))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        // When/Then
        assertThatThrownBy(() ->
                ssoAdapter.authenticate(
                        invalidJson,
                        "auth-data",
                        authParams,
                        organizationId
                ).await().indefinitely())
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("Invalid SSO configuration");
    }

    @Test
    void logout_oidcProtocol_callsOidcProvider() throws JsonProcessingException {
        // Given
        String ssoConfigJson = createOidcConfigJson();
        SsoConfig ssoConfig = createOidcSsoConfig();
        SsoAdapter.SsoLogoutParams logoutParams = new SsoAdapter.SsoLogoutParams();
        logoutParams.setIdTokenHint("id-token-hint");
        logoutParams.setPostLogoutRedirectUri("https://app.scrumpoker.com");

        when(objectMapper.readValue(ssoConfigJson, SsoConfig.class))
                .thenReturn(ssoConfig);
        when(oidcProvider.logout(
                any(OidcConfig.class),
                eq(logoutParams.getIdTokenHint()),
                eq(logoutParams.getPostLogoutRedirectUri())
        )).thenReturn(Uni.createFrom().item(true));

        // When
        Boolean result = ssoAdapter.logout(
                ssoConfigJson,
                "oidc",
                logoutParams
        ).await().indefinitely();

        // Then
        assertThat(result).isTrue();
        verify(oidcProvider).logout(
                any(OidcConfig.class),
                eq(logoutParams.getIdTokenHint()),
                eq(logoutParams.getPostLogoutRedirectUri())
        );
    }

    @Test
    void logout_saml2Protocol_callsSaml2Provider() throws JsonProcessingException {
        // Given
        String ssoConfigJson = createSaml2ConfigJson();
        SsoConfig ssoConfig = createSaml2SsoConfig();
        SsoAdapter.SsoLogoutParams logoutParams = new SsoAdapter.SsoLogoutParams();
        logoutParams.setNameId("test@example.com");
        logoutParams.setSessionIndex("session-index-123");

        when(objectMapper.readValue(ssoConfigJson, SsoConfig.class))
                .thenReturn(ssoConfig);
        when(saml2Provider.logout(
                any(Saml2Config.class),
                eq(logoutParams.getNameId()),
                eq(logoutParams.getSessionIndex())
        )).thenReturn(Uni.createFrom().item(true));

        // When
        Boolean result = ssoAdapter.logout(
                ssoConfigJson,
                "saml2",
                logoutParams
        ).await().indefinitely();

        // Then
        assertThat(result).isTrue();
        verify(saml2Provider).logout(
                any(Saml2Config.class),
                eq(logoutParams.getNameId()),
                eq(logoutParams.getSessionIndex())
        );
    }

    @Test
    void isProtocolSupported_oidc_returnsTrue() {
        // When/Then
        assertThat(ssoAdapter.isProtocolSupported("oidc")).isTrue();
        assertThat(ssoAdapter.isProtocolSupported("OIDC")).isTrue();
    }

    @Test
    void isProtocolSupported_saml2_returnsTrue() {
        // When/Then
        assertThat(ssoAdapter.isProtocolSupported("saml2")).isTrue();
        assertThat(ssoAdapter.isProtocolSupported("SAML2")).isTrue();
    }

    @Test
    void isProtocolSupported_unsupported_returnsFalse() {
        // When/Then
        assertThat(ssoAdapter.isProtocolSupported("oauth2")).isFalse();
        assertThat(ssoAdapter.isProtocolSupported("ldap")).isFalse();
        assertThat(ssoAdapter.isProtocolSupported(null)).isFalse();
    }

    @Test
    void getSupportedProtocols_returnsOidcAndSaml2() {
        // When
        String[] protocols = ssoAdapter.getSupportedProtocols();

        // Then
        assertThat(protocols)
                .hasSize(2)
                .containsExactlyInAnyOrder("oidc", "saml2");
    }

    // Helper methods

    private String createOidcConfigJson() {
        return """
                {
                    "protocol": "oidc",
                    "oidc": {
                        "issuer": "https://test-org.okta.com",
                        "clientId": "test-client-id",
                        "clientSecret": "test-client-secret"
                    }
                }
                """;
    }

    private String createSaml2ConfigJson() {
        return """
                {
                    "protocol": "saml2",
                    "saml2": {
                        "idpEntityId": "https://test-idp.com",
                        "ssoUrl": "https://test-idp.com/sso",
                        "certificate": "-----BEGIN CERTIFICATE-----\\ntest\\n-----END CERTIFICATE-----"
                    }
                }
                """;
    }

    private SsoConfig createOidcSsoConfig() {
        OidcConfig oidcConfig = new OidcConfig(
                "https://test-org.okta.com",
                "test-client-id",
                "test-client-secret"
        );

        return new SsoConfig(
                "oidc",
                oidcConfig,
                null,
                true,
                true
        );
    }

    private SsoConfig createSaml2SsoConfig() {
        Saml2Config saml2Config = new Saml2Config(
                "https://test-idp.com",
                "https://test-idp.com/sso",
                "-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----"
        );

        return new SsoConfig(
                "saml2",
                null,
                saml2Config,
                true,
                true
        );
    }
}
