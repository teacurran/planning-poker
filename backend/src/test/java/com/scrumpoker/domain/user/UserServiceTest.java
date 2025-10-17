package com.scrumpoker.domain.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scrumpoker.repository.UserPreferenceRepository;
import com.scrumpoker.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService using Mockito mocks.
 * Tests business logic in isolation without database dependencies.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    UserPreferenceRepository userPreferenceRepository;

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    UserService userService;

    private UserPreferenceConfig testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new UserPreferenceConfig();
        testConfig.deckType = "FIBONACCI";
        testConfig.timerEnabled = false;
        testConfig.emailNotifications = true;
    }

    // ===== Create User Tests =====

    @Test
    void testCreateUser_ValidInput_ReturnsUser() throws JsonProcessingException {
        // Given
        String provider = "google";
        String subject = "google-123";
        String email = "test@example.com";
        String displayName = "Test User";
        String avatarUrl = "https://avatar.com/test.jpg";
        String configJson = "{}";

        UserPreference expectedPref = new UserPreference();

        when(objectMapper.writeValueAsString(any())).thenReturn(configJson);
        when(userRepository.persist(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return Uni.createFrom().item(user);
        });
        when(userPreferenceRepository.persist(any(UserPreference.class))).thenReturn(Uni.createFrom().item(expectedPref));

        // When
        User result = userService.createUser(provider, subject, email, displayName, avatarUrl)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.email).isEqualTo(email);
        assertThat(result.displayName).isEqualTo(displayName);
        assertThat(result.oauthProvider).isEqualTo(provider);
        assertThat(result.oauthSubject).isEqualTo(subject);
        assertThat(result.avatarUrl).isEqualTo(avatarUrl);
        assertThat(result.subscriptionTier).isEqualTo(SubscriptionTier.FREE);
        verify(userRepository).persist(any(User.class));
        verify(userPreferenceRepository).persist(any(UserPreference.class));
    }

    @Test
    void testCreateUser_NullOAuthProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                userService.createUser(null, "subject", "test@example.com", "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAuth provider cannot be null or empty");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_EmptyOAuthProvider_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                userService.createUser("   ", "subject", "test@example.com", "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAuth provider cannot be null or empty");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_NullOAuthSubject_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                userService.createUser("google", null, "test@example.com", "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAuth subject cannot be null or empty");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_EmptyOAuthSubject_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                userService.createUser("google", "   ", "test@example.com", "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OAuth subject cannot be null or empty");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_InvalidEmailFormat_ThrowsException() {
        // When/Then - null email
        assertThatThrownBy(() ->
                userService.createUser("google", "subject", null, "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");

        // When/Then - empty email
        assertThatThrownBy(() ->
                userService.createUser("google", "subject", "   ", "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");

        // When/Then - invalid format
        assertThatThrownBy(() ->
                userService.createUser("google", "subject", "not-an-email", "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");

        // When/Then - missing domain
        assertThatThrownBy(() ->
                userService.createUser("google", "subject", "user@", "Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_NullDisplayName_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                userService.createUser("google", "subject", "test@example.com", null, null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display name must be between 1 and 100 characters");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_EmptyDisplayName_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                userService.createUser("google", "subject", "test@example.com", "   ", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display name must be between 1 and 100 characters");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_DisplayNameTooLong_ThrowsException() {
        // Given
        String longName = "a".repeat(101);

        // When/Then
        assertThatThrownBy(() ->
                userService.createUser("google", "subject", "test@example.com", longName, null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display name must be between 1 and 100 characters");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testCreateUser_CreatesDefaultPreferences() throws JsonProcessingException {
        // Given
        String configJson = "{}";
        User expectedUser = createTestUser("test@example.com", "Test User");
        UserPreference expectedPref = new UserPreference();

        when(objectMapper.writeValueAsString(any())).thenReturn(configJson);
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(expectedUser));
        when(userPreferenceRepository.persist(any(UserPreference.class))).thenAnswer(invocation -> {
            UserPreference pref = invocation.getArgument(0);
            assertThat(pref.defaultDeckType).isEqualTo("fibonacci");
            assertThat(pref.defaultRoomConfig).isNotNull();
            assertThat(pref.notificationSettings).isNotNull();
            return Uni.createFrom().item(expectedPref);
        });

        // When
        User result = userService.createUser("google", "subject", "test@example.com", "Test", null)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        verify(userPreferenceRepository).persist(any(UserPreference.class));
    }

    @Test
    void testCreateUser_PreferenceSerializationFailure_UsesEmptyJson() throws JsonProcessingException {
        // Given
        User expectedUser = createTestUser("test@example.com", "Test User");
        UserPreference expectedPref = new UserPreference();

        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Serialization error"));
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(expectedUser));
        when(userPreferenceRepository.persist(any(UserPreference.class))).thenAnswer(invocation -> {
            UserPreference pref = invocation.getArgument(0);
            // Should fallback to "{}"
            assertThat(pref.defaultRoomConfig).isEqualTo("{}");
            assertThat(pref.notificationSettings).isEqualTo("{}");
            return Uni.createFrom().item(expectedPref);
        });

        // When
        User result = userService.createUser("google", "subject", "test@example.com", "Test", null)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        verify(userPreferenceRepository).persist(any(UserPreference.class));
    }

    // ===== Update Profile Tests =====

    @Test
    void testUpdateProfile_ValidInput_UpdatesProfile() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("old@example.com", "Old Name");
        existingUser.userId = userId;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userRepository.persist(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return Uni.createFrom().item(user);
        });

        // When
        User result = userService.updateProfile(userId, "New Name", "https://new-avatar.jpg")
                .await().indefinitely();

        // Then
        assertThat(result.displayName).isEqualTo("New Name");
        assertThat(result.avatarUrl).isEqualTo("https://new-avatar.jpg");
        verify(userRepository).findById(userId);
        verify(userRepository).persist(any(User.class));
    }

    @Test
    void testUpdateProfile_OnlyDisplayName_UpdatesDisplayName() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Old Name");
        existingUser.userId = userId;
        existingUser.avatarUrl = "https://old-avatar.jpg";

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userRepository.persist(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return Uni.createFrom().item(user);
        });

        // When
        User result = userService.updateProfile(userId, "New Name", null)
                .await().indefinitely();

        // Then
        assertThat(result.displayName).isEqualTo("New Name");
        assertThat(result.avatarUrl).isEqualTo("https://old-avatar.jpg"); // Unchanged
        verify(userRepository).persist(any(User.class));
    }

    @Test
    void testUpdateProfile_OnlyAvatarUrl_UpdatesAvatar() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userRepository.persist(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return Uni.createFrom().item(user);
        });

        // When
        User result = userService.updateProfile(userId, null, "https://new-avatar.jpg")
                .await().indefinitely();

        // Then
        assertThat(result.displayName).isEqualTo("Test Name"); // Unchanged
        assertThat(result.avatarUrl).isEqualTo("https://new-avatar.jpg");
        verify(userRepository).persist(any(User.class));
    }

    @Test
    void testUpdateProfile_InvalidDisplayName_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));

        // When/Then - empty display name
        assertThatThrownBy(() ->
                userService.updateProfile(userId, "   ", null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display name must be between 1 and 100 characters");

        // When/Then - too long display name
        String longName = "a".repeat(101);
        assertThatThrownBy(() ->
                userService.updateProfile(userId, longName, null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Display name must be between 1 and 100 characters");

        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testUpdateProfile_UserNotFound_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                userService.updateProfile(userId, "New Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepository).findById(userId);
        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testUpdateProfile_DeletedUser_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User deletedUser = createTestUser("test@example.com", "Test Name");
        deletedUser.userId = userId;
        deletedUser.deletedAt = Instant.now();

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(deletedUser));

        // When/Then
        assertThatThrownBy(() ->
                userService.updateProfile(userId, "New Name", null)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).persist(any(User.class));
    }

    // ===== Get User By ID Tests =====

    @Test
    void testGetUserById_ExistingUser_ReturnsUser() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));

        // When
        User result = userService.getUserById(userId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.userId).isEqualTo(userId);
        assertThat(result.email).isEqualTo("test@example.com");
        verify(userRepository).findById(userId);
    }

    @Test
    void testGetUserById_NonExistentUser_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                userService.getUserById(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepository).findById(userId);
    }

    @Test
    void testGetUserById_DeletedUser_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User deletedUser = createTestUser("test@example.com", "Test Name");
        deletedUser.userId = userId;
        deletedUser.deletedAt = Instant.now();

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(deletedUser));

        // When/Then
        assertThatThrownBy(() ->
                userService.getUserById(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(userRepository).findById(userId);
    }

    // ===== Find By Email Tests =====

    @Test
    void testFindByEmail_ExistingUser_ReturnsUser() {
        // Given
        String email = "test@example.com";
        User existingUser = createTestUser(email, "Test Name");

        when(userRepository.findActiveByEmail(email)).thenReturn(Uni.createFrom().item(existingUser));

        // When
        User result = userService.findByEmail(email)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.email).isEqualTo(email);
        verify(userRepository).findActiveByEmail(email);
    }

    @Test
    void testFindByEmail_NonExistentUser_ReturnsNull() {
        // Given
        String email = "nonexistent@example.com";
        when(userRepository.findActiveByEmail(email)).thenReturn(Uni.createFrom().nullItem());

        // When
        User result = userService.findByEmail(email)
                .await().indefinitely();

        // Then
        assertThat(result).isNull();
        verify(userRepository).findActiveByEmail(email);
    }

    @Test
    void testFindByEmail_NullEmail_ReturnsNull() {
        // When
        User result = userService.findByEmail(null)
                .await().indefinitely();

        // Then
        assertThat(result).isNull();
        verify(userRepository, never()).findActiveByEmail(anyString());
    }

    @Test
    void testFindByEmail_EmptyEmail_ReturnsNull() {
        // When
        User result = userService.findByEmail("   ")
                .await().indefinitely();

        // Then
        assertThat(result).isNull();
        verify(userRepository, never()).findActiveByEmail(anyString());
    }

    // ===== Find Or Create User Tests =====

    @Test
    void testFindOrCreateUser_NewUser_CreatesUser() throws JsonProcessingException {
        // Given
        String provider = "google";
        String subject = "google-new";
        String email = "new@example.com";
        String displayName = "New User";
        String avatarUrl = "https://avatar.com/new.jpg";
        String configJson = "{}";

        User newUser = createTestUser(email, displayName);
        UserPreference newPref = new UserPreference();

        when(userRepository.findByOAuthProviderAndSubject(provider, subject))
                .thenReturn(Uni.createFrom().nullItem());
        when(objectMapper.writeValueAsString(any())).thenReturn(configJson);
        when(userRepository.persist(any(User.class))).thenReturn(Uni.createFrom().item(newUser));
        when(userPreferenceRepository.persist(any(UserPreference.class))).thenReturn(Uni.createFrom().item(newPref));

        // When
        User result = userService.findOrCreateUser(provider, subject, email, displayName, avatarUrl)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.email).isEqualTo(email);
        assertThat(result.displayName).isEqualTo(displayName);
        verify(userRepository).findByOAuthProviderAndSubject(provider, subject);
        verify(userRepository).persist(any(User.class));
        verify(userPreferenceRepository).persist(any(UserPreference.class));
    }

    @Test
    void testFindOrCreateUser_ExistingUser_ReturnsExistingUser() {
        // Given
        String provider = "microsoft";
        String subject = "microsoft-123";
        User existingUser = createTestUser("existing@example.com", "Existing User");
        existingUser.oauthProvider = provider;
        existingUser.oauthSubject = subject;

        when(userRepository.findByOAuthProviderAndSubject(provider, subject))
                .thenReturn(Uni.createFrom().item(existingUser));

        // When
        User result = userService.findOrCreateUser(provider, subject, "existing@example.com", "Existing User", null)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.email).isEqualTo("existing@example.com");
        assertThat(result.displayName).isEqualTo("Existing User");
        verify(userRepository).findByOAuthProviderAndSubject(provider, subject);
        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testFindOrCreateUser_ExistingUserWithUpdatedProfile_UpdatesFields() {
        // Given
        String provider = "google";
        String subject = "google-update";
        User existingUser = createTestUser("old@example.com", "Old Name");
        existingUser.oauthProvider = provider;
        existingUser.oauthSubject = subject;
        existingUser.avatarUrl = "https://old-avatar.jpg";

        when(userRepository.findByOAuthProviderAndSubject(provider, subject))
                .thenReturn(Uni.createFrom().item(existingUser));

        // When
        User result = userService.findOrCreateUser(provider, subject, "new@example.com", "New Name", "https://new-avatar.jpg")
                .await().indefinitely();

        // Then
        assertThat(result.email).isEqualTo("new@example.com");
        assertThat(result.displayName).isEqualTo("New Name");
        assertThat(result.avatarUrl).isEqualTo("https://new-avatar.jpg");
        verify(userRepository).findByOAuthProviderAndSubject(provider, subject);
    }

    @Test
    void testFindOrCreateUser_ExistingUserWithSameProfile_NoChanges() {
        // Given
        String provider = "google";
        String subject = "google-same";
        String email = "same@example.com";
        String displayName = "Same Name";
        String avatarUrl = "https://same-avatar.jpg";

        User existingUser = createTestUser(email, displayName);
        existingUser.oauthProvider = provider;
        existingUser.oauthSubject = subject;
        existingUser.avatarUrl = avatarUrl;

        when(userRepository.findByOAuthProviderAndSubject(provider, subject))
                .thenReturn(Uni.createFrom().item(existingUser));

        // When
        User result = userService.findOrCreateUser(provider, subject, email, displayName, avatarUrl)
                .await().indefinitely();

        // Then
        assertThat(result.email).isEqualTo(email);
        assertThat(result.displayName).isEqualTo(displayName);
        assertThat(result.avatarUrl).isEqualTo(avatarUrl);
        verify(userRepository).findByOAuthProviderAndSubject(provider, subject);
    }

    @Test
    void testFindOrCreateUser_ExistingUserWithNullEmail_SkipsEmailUpdate() {
        // Given
        String provider = "google";
        String subject = "google-null-email";
        String originalEmail = "original@example.com";
        User existingUser = createTestUser(originalEmail, "Test Name");
        existingUser.oauthProvider = provider;
        existingUser.oauthSubject = subject;

        when(userRepository.findByOAuthProviderAndSubject(provider, subject))
                .thenReturn(Uni.createFrom().item(existingUser));

        // When
        User result = userService.findOrCreateUser(provider, subject, null, "Test Name", null)
                .await().indefinitely();

        // Then
        assertThat(result.email).isEqualTo(originalEmail); // Email unchanged
        verify(userRepository).findByOAuthProviderAndSubject(provider, subject);
    }

    // ===== Get Preferences Tests =====

    @Test
    void testGetPreferences_ExistingPreferences_ReturnsPreferences() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        UserPreference existingPref = new UserPreference();
        existingPref.userId = userId;
        existingPref.defaultDeckType = "T_SHIRT";

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingPref));

        // When
        UserPreference result = userService.getPreferences(userId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.userId).isEqualTo(userId);
        assertThat(result.defaultDeckType).isEqualTo("T_SHIRT");
        verify(userRepository).findById(userId);
        verify(userPreferenceRepository).findById(userId);
    }

    @Test
    void testGetPreferences_UserNotFound_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                userService.getPreferences(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findById(userId);
        verify(userPreferenceRepository, never()).findById(any());
    }

    @Test
    void testGetPreferences_PreferencesNotFound_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                userService.getPreferences(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class);

        verify(userPreferenceRepository).findById(userId);
    }

    // ===== Get Preference Config Tests =====

    @Test
    void testGetPreferenceConfig_ValidUser_ReturnsConfig() throws JsonProcessingException {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        UserPreference existingPref = new UserPreference();
        existingPref.userId = userId;
        existingPref.defaultRoomConfig = "{\"deckType\":\"FIBONACCI\",\"timerEnabled\":true}";

        UserPreferenceConfig expectedConfig = new UserPreferenceConfig();
        expectedConfig.deckType = "FIBONACCI";
        expectedConfig.timerEnabled = true;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingPref));
        when(objectMapper.readValue(existingPref.defaultRoomConfig, UserPreferenceConfig.class))
                .thenReturn(expectedConfig);

        // When
        UserPreferenceConfig result = userService.getPreferenceConfig(userId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.deckType).isEqualTo("FIBONACCI");
        assertThat(result.timerEnabled).isTrue();
        verify(userRepository).findById(userId);
        verify(userPreferenceRepository).findById(userId);
        verify(objectMapper).readValue(existingPref.defaultRoomConfig, UserPreferenceConfig.class);
    }

    @Test
    void testGetPreferenceConfig_EmptyJson_ReturnsEmptyConfig() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        UserPreference existingPref = new UserPreference();
        existingPref.userId = userId;
        existingPref.defaultRoomConfig = "{}";

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingPref));

        // When
        UserPreferenceConfig result = userService.getPreferenceConfig(userId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findById(userId);
        verify(userPreferenceRepository).findById(userId);
    }

    @Test
    void testGetPreferenceConfig_NullJson_ReturnsEmptyConfig() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        UserPreference existingPref = new UserPreference();
        existingPref.userId = userId;
        existingPref.defaultRoomConfig = null;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingPref));

        // When
        UserPreferenceConfig result = userService.getPreferenceConfig(userId)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findById(userId);
        verify(userPreferenceRepository).findById(userId);
    }

    @Test
    void testGetPreferenceConfig_InvalidJson_ThrowsException() throws JsonProcessingException {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        UserPreference existingPref = new UserPreference();
        existingPref.userId = userId;
        existingPref.defaultRoomConfig = "{invalid json}";

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingPref));
        when(objectMapper.readValue(anyString(), any(Class.class)))
                .thenThrow(new JsonProcessingException("Invalid JSON") {});

        // When/Then
        assertThatThrownBy(() ->
                userService.getPreferenceConfig(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to deserialize user preference config");

        verify(userRepository).findById(userId);
        verify(userPreferenceRepository).findById(userId);
    }

    @Test
    void testGetPreferenceConfig_UserNotFound_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                userService.getPreferenceConfig(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findById(userId);
        verify(userPreferenceRepository, never()).findById(any());
    }

    // ===== Update Preferences Tests =====

    @Test
    void testUpdatePreferences_ValidInput_UpdatesPreferences() throws JsonProcessingException {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        UserPreference existingPref = new UserPreference();
        existingPref.userId = userId;
        existingPref.defaultDeckType = "FIBONACCI";

        String configJson = "{\"deckType\":\"T_SHIRT\"}";

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingPref));
        when(objectMapper.writeValueAsString(any())).thenReturn(configJson);
        when(userPreferenceRepository.persist(any(UserPreference.class))).thenAnswer(invocation -> {
            UserPreference pref = invocation.getArgument(0);
            return Uni.createFrom().item(pref);
        });

        // When
        UserPreference result = userService.updatePreferences(userId, testConfig)
                .await().indefinitely();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.defaultDeckType).isEqualTo("FIBONACCI");
        assertThat(result.defaultRoomConfig).isEqualTo(configJson);
        verify(userRepository).findById(userId);
        verify(userPreferenceRepository).findById(userId);
        verify(objectMapper, times(2)).writeValueAsString(testConfig);
        verify(userPreferenceRepository).persist(any(UserPreference.class));
    }

    @Test
    void testUpdatePreferences_NullConfig_ThrowsException() {
        // When/Then
        assertThatThrownBy(() ->
                userService.updatePreferences(UUID.randomUUID(), null)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Configuration cannot be null");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void testUpdatePreferences_UserNotFound_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                userService.updatePreferences(userId, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findById(userId);
        verify(userPreferenceRepository, never()).findById(any());
    }

    @Test
    void testUpdatePreferences_SerializationFailure_ThrowsException() throws JsonProcessingException {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        UserPreference existingPref = new UserPreference();
        existingPref.userId = userId;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userPreferenceRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingPref));
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("Serialization error") {});

        // When/Then
        assertThatThrownBy(() ->
                userService.updatePreferences(userId, testConfig)
                        .await().indefinitely()
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to serialize preferences");

        verify(userPreferenceRepository, never()).persist(any(UserPreference.class));
    }

    // ===== Delete User Tests =====

    @Test
    void testDeleteUser_ValidUser_SoftDeletes() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = createTestUser("test@example.com", "Test Name");
        existingUser.userId = userId;

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(existingUser));
        when(userRepository.persist(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return Uni.createFrom().item(user);
        });

        // When
        User result = userService.deleteUser(userId)
                .await().indefinitely();

        // Then
        assertThat(result.deletedAt).isNotNull();
        verify(userRepository).findById(userId);
        verify(userRepository).persist(any(User.class));
    }

    @Test
    void testDeleteUser_UserNotFound_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().nullItem());

        // When/Then
        assertThatThrownBy(() ->
                userService.deleteUser(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).persist(any(User.class));
    }

    @Test
    void testDeleteUser_AlreadyDeleted_ThrowsException() {
        // Given
        UUID userId = UUID.randomUUID();
        User deletedUser = createTestUser("test@example.com", "Test Name");
        deletedUser.userId = userId;
        deletedUser.deletedAt = Instant.now();

        when(userRepository.findById(userId)).thenReturn(Uni.createFrom().item(deletedUser));

        // When/Then
        assertThatThrownBy(() ->
                userService.deleteUser(userId)
                        .await().indefinitely()
        )
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository).findById(userId);
        verify(userRepository, never()).persist(any(User.class));
    }

    // ===== Helper Methods =====

    private User createTestUser(String email, String displayName) {
        User user = new User();
        user.userId = UUID.randomUUID();
        user.email = email;
        user.displayName = displayName;
        user.oauthProvider = "google";
        user.oauthSubject = "google-123";
        user.subscriptionTier = SubscriptionTier.FREE;
        user.deletedAt = null;
        return user;
    }
}
