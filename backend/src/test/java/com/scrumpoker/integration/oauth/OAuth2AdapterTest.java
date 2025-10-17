package com.scrumpoker.integration.oauth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

/**
 * Unit tests for OAuth2Adapter using Mockito mocks.
 * Tests routing logic and input validation without
 * testing provider-specific implementations.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2AdapterTest {

    @Mock
    private GoogleOAuthProvider googleProvider;

    @Mock
    private MicrosoftOAuthProvider microsoftProvider;

    @InjectMocks
    private OAuth2Adapter oauth2Adapter;

    private OAuthUserInfo testUserInfo;

    @BeforeEach
    void setUp() {
        testUserInfo = new OAuthUserInfo(
                "test-subject-123",
                "test@example.com",
                "Test User",
                "https://avatar.example.com/test.jpg",
                "google"
        );
    }

    // ===== Exchange Code For Token Tests =====

    @Test
    void testExchangeCodeForToken_Google_Success() {
        // Given
        final String provider = "google";
        final String code = "auth-code-123";
        final String verifier = "pkce-verifier-xyz";
        final String redirectUri = "https://app.example.com/callback";

        when(googleProvider.exchangeCodeForToken(code, verifier, redirectUri))
                .thenReturn(testUserInfo);

        // When
        final OAuthUserInfo result = oauth2Adapter.exchangeCodeForToken(
                provider, code, verifier, redirectUri);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("test-subject-123");
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(googleProvider).exchangeCodeForToken(code, verifier, redirectUri);
        verify(microsoftProvider, never()).exchangeCodeForToken(
                anyString(), anyString(), anyString());
    }

    @Test
    void testExchangeCodeForToken_Microsoft_Success() {
        // Given
        final String provider = "microsoft";
        final String code = "auth-code-456";
        final String verifier = "pkce-verifier-abc";
        final String redirectUri = "https://app.example.com/callback";

        final OAuthUserInfo microsoftUserInfo = new OAuthUserInfo(
                "ms-subject-456",
                "msuser@example.com",
                "MS User",
                "https://avatar.example.com/ms.jpg",
                "microsoft"
        );

        when(microsoftProvider.exchangeCodeForToken(code, verifier, redirectUri))
                .thenReturn(microsoftUserInfo);

        // When
        final OAuthUserInfo result = oauth2Adapter.exchangeCodeForToken(
                provider, code, verifier, redirectUri);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getSubject()).isEqualTo("ms-subject-456");
        assertThat(result.getProvider()).isEqualTo("microsoft");
        verify(microsoftProvider).exchangeCodeForToken(code, verifier, redirectUri);
        verify(googleProvider, never()).exchangeCodeForToken(
                anyString(), anyString(), anyString());
    }

    @Test
    void testExchangeCodeForToken_CaseInsensitiveProvider() {
        // Given
        final String code = "code";
        final String verifier = "verifier";
        final String redirectUri = "https://app.example.com/callback";

        when(googleProvider.exchangeCodeForToken(code, verifier, redirectUri))
                .thenReturn(testUserInfo);

        // When - test various casings
        oauth2Adapter.exchangeCodeForToken("GOOGLE", code, verifier, redirectUri);
        oauth2Adapter.exchangeCodeForToken("Google", code, verifier, redirectUri);
        oauth2Adapter.exchangeCodeForToken("google", code, verifier, redirectUri);

        // Then
        verify(googleProvider, org.mockito.Mockito.times(3))
                .exchangeCodeForToken(code, verifier, redirectUri);
    }

    @Test
    void testExchangeCodeForToken_NullProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        null, "code", "verifier", "redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider cannot be null or empty");

        verify(googleProvider, never()).exchangeCodeForToken(
                anyString(), anyString(), anyString());
        verify(microsoftProvider, never()).exchangeCodeForToken(
                anyString(), anyString(), anyString());
    }

    @Test
    void testExchangeCodeForToken_EmptyProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "   ", "code", "verifier", "redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider cannot be null or empty");
    }

    @Test
    void testExchangeCodeForToken_UnsupportedProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "facebook", "code", "verifier", "redirect"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Unsupported OAuth provider");
    }

    @Test
    void testExchangeCodeForToken_NullAuthorizationCode_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "google", null, "verifier", "redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authorization code cannot be null or empty");
    }

    @Test
    void testExchangeCodeForToken_EmptyAuthorizationCode_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "google", "   ", "verifier", "redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authorization code cannot be null or empty");
    }

    @Test
    void testExchangeCodeForToken_NullCodeVerifier_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "google", "code", null, "redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code verifier cannot be null or empty");
    }

    @Test
    void testExchangeCodeForToken_EmptyCodeVerifier_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "google", "code", "   ", "redirect"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Code verifier cannot be null or empty");
    }

    @Test
    void testExchangeCodeForToken_NullRedirectUri_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "google", "code", "verifier", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redirect URI cannot be null or empty");
    }

    @Test
    void testExchangeCodeForToken_EmptyRedirectUri_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.exchangeCodeForToken(
                        "google", "code", "verifier", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Redirect URI cannot be null or empty");
    }

    // ===== Validate ID Token Tests =====

    @Test
    void testValidateIdToken_Google_Success() {
        // Given
        final String provider = "google";
        final String idToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...";

        when(googleProvider.validateAndExtractClaims(idToken))
                .thenReturn(testUserInfo);

        // When
        final OAuthUserInfo result =
                oauth2Adapter.validateIdToken(provider, idToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("test@example.com");
        verify(googleProvider).validateAndExtractClaims(idToken);
        verify(microsoftProvider, never()).validateAndExtractClaims(anyString());
    }

    @Test
    void testValidateIdToken_Microsoft_Success() {
        // Given
        final String provider = "microsoft";
        final String idToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...";

        final OAuthUserInfo microsoftUserInfo = new OAuthUserInfo(
                "ms-subject-789",
                "msuser@example.com",
                "MS User",
                null,
                "microsoft"
        );

        when(microsoftProvider.validateAndExtractClaims(idToken))
                .thenReturn(microsoftUserInfo);

        // When
        final OAuthUserInfo result =
                oauth2Adapter.validateIdToken(provider, idToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getProvider()).isEqualTo("microsoft");
        verify(microsoftProvider).validateAndExtractClaims(idToken);
    }

    @Test
    void testValidateIdToken_NullProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.validateIdToken(null, "id-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider cannot be null or empty");
    }

    @Test
    void testValidateIdToken_EmptyProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.validateIdToken("   ", "id-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Provider cannot be null or empty");
    }

    @Test
    void testValidateIdToken_UnsupportedProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.validateIdToken("github", "id-token"))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .hasMessageContaining("Unsupported OAuth provider");
    }

    @Test
    void testValidateIdToken_NullIdToken_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.validateIdToken("google", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID token cannot be null or empty");
    }

    @Test
    void testValidateIdToken_EmptyIdToken_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                oauth2Adapter.validateIdToken("google", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID token cannot be null or empty");
    }

    // ===== Helper Method Tests =====

    @Test
    void testGetSupportedProviders_ReturnsCorrectList() {
        // When
        final String[] providers = oauth2Adapter.getSupportedProviders();

        // Then
        assertThat(providers).containsExactlyInAnyOrder("google", "microsoft");
    }

    @Test
    void testIsProviderSupported_Google_ReturnsTrue() {
        // When/Then
        assertThat(oauth2Adapter.isProviderSupported("google")).isTrue();
        assertThat(oauth2Adapter.isProviderSupported("GOOGLE")).isTrue();
        assertThat(oauth2Adapter.isProviderSupported("Google")).isTrue();
    }

    @Test
    void testIsProviderSupported_Microsoft_ReturnsTrue() {
        // When/Then
        assertThat(oauth2Adapter.isProviderSupported("microsoft")).isTrue();
        assertThat(oauth2Adapter.isProviderSupported("MICROSOFT")).isTrue();
        assertThat(oauth2Adapter.isProviderSupported("Microsoft")).isTrue();
    }

    @Test
    void testIsProviderSupported_Unsupported_ReturnsFalse() {
        // When/Then
        assertThat(oauth2Adapter.isProviderSupported("facebook")).isFalse();
        assertThat(oauth2Adapter.isProviderSupported("github")).isFalse();
        assertThat(oauth2Adapter.isProviderSupported("twitter")).isFalse();
    }

    @Test
    void testIsProviderSupported_Null_ReturnsFalse() {
        // When/Then
        assertThat(oauth2Adapter.isProviderSupported(null)).isFalse();
    }

    @Test
    void testIsProviderSupported_Empty_ReturnsFalse() {
        // When/Then
        assertThat(oauth2Adapter.isProviderSupported("")).isFalse();
        assertThat(oauth2Adapter.isProviderSupported("   ")).isFalse();
    }
}
