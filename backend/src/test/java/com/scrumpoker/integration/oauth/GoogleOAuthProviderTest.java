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
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for GoogleOAuthProvider.
 * Tests ID token validation and claim extraction logic
 * using mocked JWT parsing.
 */
@ExtendWith(MockitoExtension.class)
class GoogleOAuthProviderTest {

    @Mock
    private JWTParser jwtParser;

    @Mock
    private JsonWebToken jwt;

    @InjectMocks
    private GoogleOAuthProvider googleProvider;

    private static final String TEST_CLIENT_ID = "test-google-client-id";
    private static final String VALID_ID_TOKEN = "eyJhbGc...valid.token";
    private static final String GOOGLE_ISSUER =
            "https://accounts.google.com";

    @BeforeEach
    void setUp() throws Exception {
        // Use reflection to set private fields for testing
        setPrivateField(googleProvider, "clientId", TEST_CLIENT_ID);
        setPrivateField(googleProvider, "clientSecret",
                "test-secret");
    }

    // ===== Validate And Extract Claims Tests =====

    @Test
    void testValidateAndExtractClaims_ValidToken_Success()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600; // 1 hour from now

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("google-subject-123");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("Test User");
        when(jwt.getClaim("picture"))
                .thenReturn("https://avatar.example.com/test.jpg");

        // When
        final OAuthUserInfo result =
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("google-subject-123");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        assertThat(result.getName()).isEqualTo("Test User");
        assertThat(result.getAvatarUrl())
                .isEqualTo("https://avatar.example.com/test.jpg");
        assertThat(result.getProvider()).isEqualTo("google");
        verify(jwtParser).parse(VALID_ID_TOKEN);
    }

    @Test
    void testValidateAndExtractClaims_AlternativeIssuer_Success()
            throws ParseException {
        // Given - Google also uses "accounts.google.com" without https
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn("accounts.google.com");
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("google-subject-456");
        when(jwt.getClaim("email")).thenReturn("user@example.com");
        when(jwt.getClaim("name")).thenReturn("User Name");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("google-subject-456");
        assertThat(result.getAvatarUrl()).isNull();
    }

    @Test
    void testValidateAndExtractClaims_MissingName_UsesEmailFallback()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("google-subject-789");
        when(jwt.getClaim("email")).thenReturn("john.doe@example.com");
        when(jwt.getClaim("name")).thenReturn(null);
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("john.doe");
        // Name extracted from email username
    }

    @Test
    void testValidateAndExtractClaims_EmptyName_UsesEmailFallback()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("google-subject-empty-name");
        when(jwt.getClaim("email")).thenReturn("test@example.com");
        when(jwt.getClaim("name")).thenReturn("");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result.getName()).isEqualTo("test");
    }

    @Test
    void testValidateAndExtractClaims_ExpiredToken_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime - 3600;
        // Expired 1 hour ago

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);

        // When/Then
        assertThatThrownBy(() ->
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
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
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
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
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
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
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton("wrong-client-id"));

        // When/Then
        assertThatThrownBy(() ->
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining(
                        "ID token audience does not match client ID");
    }

    @Test
    void testValidateAndExtractClaims_MultipleAudiences_IncludesClientId()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        final Set<String> audiences = new HashSet<>();
        audiences.add("other-client-id");
        audiences.add(TEST_CLIENT_ID);

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience()).thenReturn(audiences);
        when(jwt.getSubject()).thenReturn("google-subject-multi-aud");
        when(jwt.getClaim("email")).thenReturn("multi@example.com");
        when(jwt.getClaim("name")).thenReturn("Multi Aud User");
        when(jwt.getClaim("picture")).thenReturn(null);

        // When
        final OAuthUserInfo result =
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("multi@example.com");
    }

    @Test
    void testValidateAndExtractClaims_MissingSubject_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() ->
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Missing 'sub' claim in ID token");
    }

    @Test
    void testValidateAndExtractClaims_EmptySubject_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("");

        // When/Then
        assertThatThrownBy(() ->
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Missing 'sub' claim in ID token");
    }

    @Test
    void testValidateAndExtractClaims_MissingEmail_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("google-subject-no-email");
        when(jwt.getClaim("email")).thenReturn(null);

        // When/Then
        assertThatThrownBy(() ->
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Missing 'email' claim in ID token");
    }

    @Test
    void testValidateAndExtractClaims_EmptyEmail_ThrowsException()
            throws ParseException {
        // Given
        final long currentTime = System.currentTimeMillis() / 1000;
        final long expirationTime = currentTime + 3600;

        when(jwtParser.parse(VALID_ID_TOKEN)).thenReturn(jwt);
        when(jwt.getExpirationTime()).thenReturn(expirationTime);
        when(jwt.getIssuer()).thenReturn(GOOGLE_ISSUER);
        when(jwt.getAudience())
                .thenReturn(Collections.singleton(TEST_CLIENT_ID));
        when(jwt.getSubject()).thenReturn("google-subject-empty-email");
        when(jwt.getClaim("email")).thenReturn("");

        // When/Then
        assertThatThrownBy(() ->
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Missing 'email' claim in ID token");
    }

    @Test
    void testValidateAndExtractClaims_ParseException_ThrowsOAuthException()
            throws ParseException {
        // Given
        when(jwtParser.parse(anyString()))
                .thenThrow(new ParseException("Invalid JWT format"));

        // When/Then
        assertThatThrownBy(() ->
                googleProvider.validateAndExtractClaims(VALID_ID_TOKEN))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Invalid ID token")
                .hasCauseInstanceOf(ParseException.class);
    }

    @Test
    void testGetProviderName_ReturnsGoogle() {
        // When/Then
        assertThat(googleProvider.getProviderName()).isEqualTo("google");
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
