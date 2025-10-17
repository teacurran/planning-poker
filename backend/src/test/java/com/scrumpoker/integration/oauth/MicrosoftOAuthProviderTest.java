package com.scrumpoker.integration.oauth;

import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for MicrosoftOAuthProvider.
 * Tests ID token validation and claim extraction logic
 * with Microsoft-specific behaviors.
 */
@ExtendWith(MockitoExtension.class)
class MicrosoftOAuthProviderTest {

    @Mock
    private JWTParser jwtParser;

    @Mock
    private JsonWebToken jwt;

    @InjectMocks
    private MicrosoftOAuthProvider microsoftProvider;

    private static final String TEST_CLIENT_ID =
            "test-microsoft-client-id";
    private static final String VALID_ID_TOKEN = "eyJhbGc...valid.token";
    private static final String MS_ISSUER_V2 =
            "https://login.microsoftonline.com/common/v2.0";
    private static final String MS_ISSUER_LEGACY =
            "https://sts.windows.net/tenant-id/";

    @BeforeEach
    void setUp() throws Exception {
        setPrivateField(microsoftProvider, "clientId", TEST_CLIENT_ID);
        setPrivateField(microsoftProvider, "clientSecret",
                "test-secret");
    }

    // ===== Validate And Extract Claims Tests =====

    @Test
    void testValidateAndExtractClaims_ValidToken_Success()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("ms-subject-123");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getClaim("picture"))
                .thenReturn("https://avatar.example.com/test.jpg");

        // When
        final OAuthUserInfo result =
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("ms-subject-123");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getName()).isEqualTo("Test User");
        assertThat(result.getAvatarUrl())
                .isEqualTo("https://avatar.example.com/test.jpg");
        assertThat(result.getProvider()).isEqualTo("microsoft");
    }

    @Test
    void testValidateAndExtractClaims_LegacyIssuer_Success()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_LEGACY);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("ms-subject-456");
        when(jwt.getClaim("email")).thenReturn("legacy@example.com");
        when(jwt.getClaim("name")).thenReturn("Legacy User");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("ms-subject-456");
        assertThat(result.getEmail()).isEqualTo("legacy@example.com");
    }

    @Test
    void testValidateAndExtractClaims_PreferredUsername_Fallback()
            throws ParseException {
        // Given - Microsoft uses preferred_username if email is missing
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("ms-subject-789");
        when(jwt.getClaim("email")).thenReturn(null);
        when(jwt.getClaim("preferred_username"))
                .thenReturn("user@example.com");
        when(jwt.getClaim("name")).thenReturn("User Name");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void testValidateAndExtractClaims_EmptyEmail_UsesPreferredUsername()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("ms-subject-empty-email");
        when(jwt.getClaim("email")).thenReturn("");
        when(jwt.getClaim("preferred_username"))
                .thenReturn("preferred@example.com");
        when(jwt.getClaim("name")).thenReturn("Preferred User");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result.getEmail())
                .isEqualTo("preferred@example.com");
    }

    @Test
    void testValidateAndExtractClaims_MissingName_UsesEmailFallback()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("ms-subject-no-name");
        when(jwt.getClaim("email")).thenReturn("john.doe@example.com");
        when(jwt.getClaim("name")).thenReturn(null);
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result.getName()).isEqualTo("john.doe");
    }

    @Test
    void testValidateAndExtractClaims_ExpiredToken_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime - 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("ID token has expired");
    }

    @Test
    void testValidateAndExtractClaims_InvalidIssuer_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn("https://evil.com");

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Invalid ID token issuer");
    }

    @Test
    void testValidateAndExtractClaims_NullIssuer_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Invalid ID token issuer");
    }

    @Test
    void testValidateAndExtractClaims_InvalidAudience_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton("wrong-client-id"));

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining(
                        "ID token audience does not match client ID");
    }

    @Test
    void testValidateAndExtractClaims_MissingSubject_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Missing 'sub' claim in ID token");
    }

    @Test
    void testValidateAndExtractClaims_MissingEmailAndPreferredUsername()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("ms-subject-no-email");
        when(jwt.getClaim("email")).thenReturn(null);
        when(jwt.getClaim("preferred_username")).thenReturn(null);
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining(
                        "Missing 'email' or 'preferred_username' claim");
    }

    @Test
    void testValidateAndExtractClaims_BothEmailAndPreferredUsernameEmpty()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(MS_ISSUER_V2);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("ms-subject-both-empty");
        when(jwt.getClaim("email")).thenReturn("");
        when(jwt.getClaim("preferred_username")).thenReturn("");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining(
                        "Missing 'email' or 'preferred_username' claim");
    }

    @Test
    void testValidateAndExtractClaims_ParseException_ThrowsOAuthException()
            throws ParseException {
        // Given
        when(jwtParser.parse(anyString()))
                .thenThrow(new ParseException("Invalid JWT format"));

        // When/Then
        assertThatThrownBy(() ->
                microsoftProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Invalid ID token")
                .hasCauseInstanceOf(ParseException.class);
    }

    @Test
    void testGetProviderName_ReturnsMicrosoft() {
        // When/Then
        assertThat(microsoftProvider.getProviderName())
                .isEqualTo("microsoft");
    }

    // ===== Helper Methods =====

    /**
     * Helper method to set private fields using reflection.
     */
    private void setPrivateField(final Object target,
                                  final String fieldName,
                                  final Object value) throws Exception {
        final java.lang.reflect.Field field =
                target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
