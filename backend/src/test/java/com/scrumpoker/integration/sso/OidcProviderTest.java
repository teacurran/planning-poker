package com.scrumpoker.integration.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Unit tests for {@link OidcProvider}.
 * Tests OIDC token validation and user attribute extraction.
 */
@ExtendWith(MockitoExtension.class)
class OidcProviderTest {

    @Mock
    private JWTParser jwtParser;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OidcProvider oidcProvider;

    private OidcConfig oidcConfig;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        organizationId = UUID.randomUUID();
        oidcConfig = new OidcConfig(
                "https://test-org.okta.com",
                "test-client-id",
                "test-client-secret"
        );
    }

    @Test
    void validateAndExtractClaims_validToken_extractsUserInfo() throws ParseException {
        // Given
        String idToken = "valid.id.token";
        JsonWebToken jwt = createMockJwt(
                "test-subject",
                "test@example.com",
                "Test User",
                Arrays.asList("Admin", "Users")
        );

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When
        SsoUserInfo userInfo = oidcProvider.validateAndExtractClaims(
                idToken, oidcConfig, organizationId);

        // Then
        assertThat(userInfo).isNotNull();
        assertThat(userInfo.getSubject()).isEqualTo("test-subject");
        assertThat(userInfo.getEmail()).isEqualTo("test@example.com");
        assertThat(userInfo.getName()).isEqualTo("Test User");
        assertThat(userInfo.getProtocol()).isEqualTo("oidc");
        assertThat(userInfo.getOrganizationId()).isEqualTo(organizationId);
        assertThat(userInfo.getGroups()).containsExactlyInAnyOrder("Admin", "Users");
    }

    @Test
    void validateAndExtractClaims_expiredToken_throwsException() throws ParseException {
        // Given
        String idToken = "expired.id.token";
        JsonWebToken jwt = createExpiredMockJwt();

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When/Then
        assertThatThrownBy(() ->
                oidcProvider.validateAndExtractClaims(idToken, oidcConfig, organizationId))
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validateAndExtractClaims_invalidIssuer_throwsException() throws ParseException {
        // Given
        String idToken = "invalid.issuer.token";
        JsonWebToken jwt = createMockJwtWithInvalidIssuer();

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When/Then
        assertThatThrownBy(() ->
                oidcProvider.validateAndExtractClaims(idToken, oidcConfig, organizationId))
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("Invalid ID token issuer");
    }

    @Test
    void validateAndExtractClaims_invalidAudience_throwsException() throws ParseException {
        // Given
        String idToken = "invalid.audience.token";
        JsonWebToken jwt = createMockJwtWithInvalidAudience();

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When/Then
        assertThatThrownBy(() ->
                oidcProvider.validateAndExtractClaims(idToken, oidcConfig, organizationId))
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("audience does not match client ID");
    }

    @Test
    void validateAndExtractClaims_missingSubject_throwsException() throws ParseException {
        // Given
        String idToken = "missing.subject.token";
        JsonWebToken jwt = createMockJwtWithoutSubject();

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When/Then
        assertThatThrownBy(() ->
                oidcProvider.validateAndExtractClaims(idToken, oidcConfig, organizationId))
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("Missing 'sub' claim");
    }

    @Test
    void validateAndExtractClaims_missingEmail_throwsException() throws ParseException {
        // Given
        String idToken = "missing.email.token";
        JsonWebToken jwt = createMockJwtWithoutEmail();

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When/Then
        assertThatThrownBy(() ->
                oidcProvider.validateAndExtractClaims(idToken, oidcConfig, organizationId))
                .isInstanceOf(SsoAuthenticationException.class)
                .hasMessageContaining("Missing 'email' claim");
    }

    @Test
    void validateAndExtractClaims_missingName_usesEmailAsName() throws ParseException {
        // Given
        String idToken = "missing.name.token";
        JsonWebToken jwt = createMockJwtWithoutName();

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When
        SsoUserInfo userInfo = oidcProvider.validateAndExtractClaims(
                idToken, oidcConfig, organizationId);

        // Then
        assertThat(userInfo.getName()).isEqualTo("test");
    }

    @Test
    void validateAndExtractClaims_groupsClaimAsList_extractsGroups() throws ParseException {
        // Given
        String idToken = "groups.list.token";
        JsonWebToken jwt = createMockJwt(
                "test-subject",
                "test@example.com",
                "Test User",
                Arrays.asList("Group1", "Group2", "Group3")
        );

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When
        SsoUserInfo userInfo = oidcProvider.validateAndExtractClaims(
                idToken, oidcConfig, organizationId);

        // Then
        assertThat(userInfo.getGroups())
                .hasSize(3)
                .containsExactlyInAnyOrder("Group1", "Group2", "Group3");
    }

    @Test
    void validateAndExtractClaims_groupsClaimAsString_extractsSingleGroup() throws ParseException {
        // Given
        String idToken = "groups.string.token";
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("test-subject");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getClaim("groups")).thenReturn("SingleGroup");
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When
        SsoUserInfo userInfo = oidcProvider.validateAndExtractClaims(
                idToken, oidcConfig, organizationId);

        // Then
        assertThat(userInfo.getGroups())
                .hasSize(1)
                .containsExactly("SingleGroup");
    }

    @Test
    void validateAndExtractClaims_noGroups_returnsEmptyGroupsList() throws ParseException {
        // Given
        String idToken = "no.groups.token";
        JsonWebToken jwt = createMockJwt(
                "test-subject",
                "test@example.com",
                "Test User",
                Collections.emptyList()
        );

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When
        SsoUserInfo userInfo = oidcProvider.validateAndExtractClaims(
                idToken, oidcConfig, organizationId);

        // Then
        assertThat(userInfo.getGroups()).isEmpty();
    }

    @Test
    void validateAndExtractClaims_rolesClaimFallback_extractsRolesWhenNoGroups() throws ParseException {
        // Given
        String idToken = "roles.only.token";
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("test-subject");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getClaim("groups")).thenReturn(null);
        when(jwt.getClaim("roles")).thenReturn(Arrays.asList("Role1", "Role2"));
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        when(jwtParser.parse(idToken)).thenReturn(jwt);

        // When
        SsoUserInfo userInfo = oidcProvider.validateAndExtractClaims(
                idToken, oidcConfig, organizationId);

        // Then
        assertThat(userInfo.getGroups())
                .hasSize(2)
                .containsExactlyInAnyOrder("Role1", "Role2");
    }

    @Test
    void getProtocolName_returnsOidc() {
        // When/Then
        assertThat(oidcProvider.getProtocolName()).isEqualTo("oidc");
    }

    // Helper methods to create mock JWT tokens

    private JsonWebToken createMockJwt(
            String subject,
            String email,
            String name,
            List<String> groups) {

        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn(subject);
        when(jwt.getClaim("email")).thenReturn(email);
        when(jwt.getClaim("name")).thenReturn(name);
        when(jwt.getClaim("groups")).thenReturn(groups);
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        return jwt;
    }

    private JsonWebToken createExpiredMockJwt() {
        JsonWebToken jwt = mock(JsonWebToken.class, withSettings().lenient());
        when(jwt.getSubject()).thenReturn("test-subject");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        // Token expired 1 hour ago
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 - 3600);

        return jwt;
    }

    private JsonWebToken createMockJwtWithInvalidIssuer() {
        JsonWebToken jwt = mock(JsonWebToken.class, withSettings().lenient());
        when(jwt.getSubject()).thenReturn("test-subject");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getIssuer()).thenReturn("https://wrong-issuer.com");
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        return jwt;
    }

    private JsonWebToken createMockJwtWithInvalidAudience() {
        JsonWebToken jwt = mock(JsonWebToken.class, withSettings().lenient());
        when(jwt.getSubject()).thenReturn("test-subject");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of("wrong-client-id"));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        return jwt;
    }

    private JsonWebToken createMockJwtWithoutSubject() {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn(null);
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        return jwt;
    }

    private JsonWebToken createMockJwtWithoutEmail() {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("test-subject");
        when(jwt.getClaim("email")).thenReturn(null);
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        return jwt;
    }

    private JsonWebToken createMockJwtWithoutName() {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getSubject()).thenReturn("test-subject");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn(null);
        when(jwt.getIssuer()).thenReturn(oidcConfig.getIssuer());
        when(jwt.getAudience()).thenReturn(Set.of(oidcConfig.getClientId()));
        when(jwt.getExpirationTime()).thenReturn(System.currentTimeMillis() / 1000 + 3600);

        return jwt;
    }
}
