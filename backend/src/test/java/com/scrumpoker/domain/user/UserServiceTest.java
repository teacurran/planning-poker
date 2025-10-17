package com.scrumpoker.domain.user;

import com.scrumpoker.repository.UserPreferenceRepository;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for UserService.
 * Tests user creation from OAuth profiles, profile updates, preference management,
 * JSONB serialization, soft delete behavior, and business validation.
 */
@QuarkusTest
class UserServiceTest {

    @Inject
    UserService userService;

    @Inject
    UserRepository userRepository;

    @Inject
    UserPreferenceRepository userPreferenceRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up test data before each test
        asserter.execute(() ->
            Panache.withTransaction(() ->
                userPreferenceRepository.deleteAll()
                    .flatMap(v -> userRepository.deleteAll())
            )
        );
    }

    // ===== User Creation Tests =====

    @Test
    @RunOnVertxContext
    void testCreateUser_ValidOAuthProfile(UniAsserter asserter) {
        // Given: valid OAuth profile data
        String provider = "google";
        String subject = "google-123456";
        String email = "test@example.com";
        String displayName = "Test User";
        String avatarUrl = "https://example.com/avatar.jpg";

        // When: creating user
        asserter.assertThat(() ->
            userService.createUser(provider, subject, email, displayName, avatarUrl)
        , user -> {
            // Then: user is created with correct fields
            assertThat(user.userId).isNotNull();
            assertThat(user.oauthProvider).isEqualTo("google");
            assertThat(user.oauthSubject).isEqualTo("google-123456");
            assertThat(user.email).isEqualTo("test@example.com");
            assertThat(user.displayName).isEqualTo("Test User");
            assertThat(user.avatarUrl).isEqualTo(avatarUrl);
            assertThat(user.subscriptionTier).isEqualTo(SubscriptionTier.FREE);
            assertThat(user.deletedAt).isNull();
        });

        // And: verify UserPreference was created
        asserter.assertThat(() -> Panache.withSession(() ->
            userPreferenceRepository.findAll().list()
        ), prefs -> {
            assertThat(prefs).hasSize(1);
            assertThat(prefs.get(0).defaultDeckType).isEqualTo("fibonacci");
            assertThat(prefs.get(0).theme).isEqualTo("light");
            assertThat(prefs.get(0).defaultRoomConfig).isNotNull();
            assertThat(prefs.get(0).notificationSettings).isNotNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateUser_ValidatesEmail(UniAsserter asserter) {
        // Given: valid OAuth data but invalid emails
        String provider = "google";
        String subject = "google-123";
        String displayName = "Test User";

        // When/Then: null email returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, subject, null, displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Invalid email format");
        });

        // When/Then: empty email returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, subject, "   ", displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Invalid email format");
        });

        // When/Then: invalid email format returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, subject, "not-an-email", displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Invalid email format");
        });

        // When/Then: email without domain returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, subject, "user@", displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Invalid email format");
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateUser_ValidatesDisplayName(UniAsserter asserter) {
        // Given: valid OAuth data but invalid display names
        String provider = "google";
        String subject = "google-123";
        String email = "test@example.com";

        // When/Then: null display name returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, subject, email, null, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Display name must be between 1 and 100 characters");
        });

        // When/Then: empty display name returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, subject, email, "   ", null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Display name must be between 1 and 100 characters");
        });

        // When/Then: display name exceeding 100 characters returns failed Uni
        String longDisplayName = "a".repeat(101);
        asserter.assertFailedWith(() ->
            userService.createUser(provider, subject, email, longDisplayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Display name must be between 1 and 100 characters");
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateUser_ValidatesOAuthProvider(UniAsserter asserter) {
        // Given: valid email and display name but invalid OAuth provider
        String subject = "google-123";
        String email = "test@example.com";
        String displayName = "Test User";

        // When/Then: null provider returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(null, subject, email, displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("OAuth provider cannot be null or empty");
        });

        // When/Then: empty provider returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser("   ", subject, email, displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("OAuth provider cannot be null or empty");
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateUser_ValidatesOAuthSubject(UniAsserter asserter) {
        // Given: valid email and display name but invalid OAuth subject
        String provider = "google";
        String email = "test@example.com";
        String displayName = "Test User";

        // When/Then: null subject returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, null, email, displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("OAuth subject cannot be null or empty");
        });

        // When/Then: empty subject returns failed Uni
        asserter.assertFailedWith(() ->
            userService.createUser(provider, "   ", email, displayName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("OAuth subject cannot be null or empty");
        });
    }

    @Test
    @RunOnVertxContext
    void testCreateUser_CreatesDefaultPreferences(UniAsserter asserter) {
        // Given: valid OAuth profile data
        String provider = "microsoft";
        String subject = "microsoft-456";
        String email = "user@company.com";
        String displayName = "Corporate User";

        // When: creating user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser(provider, subject, email, displayName, null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // Then: verify UserPreference was created with correct JSONB values
        asserter.assertThat(() -> Panache.withSession(() ->
            userPreferenceRepository.findById(userIdHolder[0])
        ), pref -> {
            assertThat(pref).isNotNull();
            assertThat(pref.userId).isEqualTo(userIdHolder[0]);
            assertThat(pref.defaultDeckType).isEqualTo("fibonacci");
            assertThat(pref.theme).isEqualTo("light");

            // Verify JSONB fields are not null and contain valid JSON
            assertThat(pref.defaultRoomConfig).isNotNull();
            assertThat(pref.defaultRoomConfig).isNotBlank();
            assertThat(pref.notificationSettings).isNotNull();
            assertThat(pref.notificationSettings).isNotBlank();
        });
    }

    // ===== Profile Update Tests =====

    @Test
    @RunOnVertxContext
    void testUpdateProfile_UpdatesDisplayNameAndAvatar(UniAsserter asserter) {
        // Given: an existing user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-789", "update@example.com", "Original Name", "https://old-avatar.jpg")
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When: updating display name and avatar URL
        asserter.execute(() ->
            userService.updateProfile(userIdHolder[0], "Updated Name", "https://new-avatar.jpg")
        );

        // Then: both fields are updated
        asserter.assertThat(() ->
            userService.getUserById(userIdHolder[0])
        , user -> {
            assertThat(user.displayName).isEqualTo("Updated Name");
            assertThat(user.avatarUrl).isEqualTo("https://new-avatar.jpg");
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateProfile_ValidatesDisplayName(UniAsserter asserter) {
        // Given: an existing user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-999", "validate@example.com", "Original", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When/Then: updating with invalid display name returns failed Uni
        String longName = "a".repeat(101);
        asserter.assertFailedWith(() ->
            userService.updateProfile(userIdHolder[0], longName, null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Display name must be between 1 and 100 characters");
        });

        // When/Then: updating with empty display name returns failed Uni
        asserter.assertFailedWith(() ->
            userService.updateProfile(userIdHolder[0], "   ", null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Display name must be between 1 and 100 characters");
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateProfile_ThrowsExceptionForNonExistentUser(UniAsserter asserter) {
        // Given: a non-existent user ID
        UUID nonExistentId = UUID.randomUUID();

        // When/Then: updating non-existent user throws UserNotFoundException
        asserter.assertFailedWith(() ->
            userService.updateProfile(nonExistentId, "New Name", null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(UserNotFoundException.class);
            assertThat(thrown.getMessage()).contains(nonExistentId.toString());
        });
    }

    // ===== Find User Tests =====

    @Test
    @RunOnVertxContext
    void testGetUserById_ReturnsUser(UniAsserter asserter) {
        // Given: an existing user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-find", "find@example.com", "Find Me", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When: finding the user by ID
        asserter.assertThat(() ->
            userService.getUserById(userIdHolder[0])
        , found -> {
            // Then: user is found
            assertThat(found).isNotNull();
            assertThat(found.userId).isEqualTo(userIdHolder[0]);
            assertThat(found.email).isEqualTo("find@example.com");
            assertThat(found.displayName).isEqualTo("Find Me");
            assertThat(found.deletedAt).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testGetUserById_ThrowsExceptionForDeletedUser(UniAsserter asserter) {
        // Given: a deleted user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-deleted", "deleted@example.com", "Deleted User", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        asserter.execute(() ->
            userService.deleteUser(userIdHolder[0])
        );

        // When/Then: getting deleted user throws UserNotFoundException
        asserter.assertFailedWith(() ->
            userService.getUserById(userIdHolder[0])
        , thrown -> {
            assertThat(thrown).isInstanceOf(UserNotFoundException.class);
            assertThat(thrown.getMessage()).contains(userIdHolder[0].toString());
        });
    }

    @Test
    @RunOnVertxContext
    void testGetUserById_ThrowsExceptionForNonExistentUser(UniAsserter asserter) {
        // Given: a non-existent user ID
        UUID nonExistentId = UUID.randomUUID();

        // When/Then: finding non-existent user throws UserNotFoundException
        asserter.assertFailedWith(() ->
            userService.getUserById(nonExistentId)
        , thrown -> {
            assertThat(thrown).isInstanceOf(UserNotFoundException.class);
            assertThat(thrown.getMessage()).contains(nonExistentId.toString());
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByEmail_ReturnsActiveUser(UniAsserter asserter) {
        // Given: an existing active user
        asserter.execute(() ->
            userService.createUser("google", "google-email", "findme@example.com", "Email User", null)
        );

        // When: finding user by email
        asserter.assertThat(() ->
            userService.findByEmail("findme@example.com")
        , found -> {
            // Then: user is found
            assertThat(found).isNotNull();
            assertThat(found.email).isEqualTo("findme@example.com");
            assertThat(found.displayName).isEqualTo("Email User");
            assertThat(found.deletedAt).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByEmail_ReturnsNullForDeletedUser(UniAsserter asserter) {
        // Given: a deleted user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-email-del", "deleted-email@example.com", "Deleted Email User", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        asserter.execute(() ->
            userService.deleteUser(userIdHolder[0])
        );

        // When: finding deleted user by email
        asserter.assertThat(() ->
            userService.findByEmail("deleted-email@example.com")
        , found -> {
            // Then: null is returned (deleted users are not found)
            assertThat(found).isNull();
        });
    }

    // ===== Preference Update Tests =====

    @Test
    @RunOnVertxContext
    void testUpdatePreferences_UpdatesJSONBFields(UniAsserter asserter) {
        // Given: an existing user with preferences
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-prefs", "prefs@example.com", "Prefs User", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When: updating preferences
        UserPreferenceConfig config = new UserPreferenceConfig();
        config.deckType = "T_SHIRT";
        config.timerEnabled = true;
        config.emailNotifications = false;

        asserter.execute(() ->
            userService.updatePreferences(userIdHolder[0], config)
        );

        // Then: JSONB fields are updated correctly
        asserter.assertThat(() -> Panache.withSession(() ->
            userPreferenceRepository.findById(userIdHolder[0])
        ), pref -> {
            assertThat(pref).isNotNull();
            assertThat(pref.defaultDeckType).isEqualTo("T_SHIRT");

            // Verify JSONB fields contain the updated values
            assertThat(pref.defaultRoomConfig).isNotNull();
            assertThat(pref.defaultRoomConfig).contains("T_SHIRT");
            assertThat(pref.defaultRoomConfig).contains("timerEnabled"); // Jackson uses camelCase

            assertThat(pref.notificationSettings).isNotNull();
            assertThat(pref.notificationSettings).contains("emailNotifications");
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdatePreferences_ThrowsExceptionForNullConfig(UniAsserter asserter) {
        // Given: an existing user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-null-config", "null-config@example.com", "Config User", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When/Then: updating with null config throws exception
        asserter.assertFailedWith(() ->
            userService.updatePreferences(userIdHolder[0], null)
        , thrown -> {
            assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
            assertThat(thrown.getMessage()).contains("Configuration cannot be null");
        });
    }

    // ===== Soft Delete Tests =====

    @Test
    @RunOnVertxContext
    void testDeleteUser_SoftDeletesUser(UniAsserter asserter) {
        // Given: an existing user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-soft-del", "soft-delete@example.com", "Delete Me", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When: deleting the user
        asserter.execute(() ->
            userService.deleteUser(userIdHolder[0])
        );

        // Then: user has deletedAt timestamp set
        asserter.assertThat(() -> Panache.withSession(() ->
            userRepository.findById(userIdHolder[0])
        ), deleted -> {
            assertThat(deleted).isNotNull(); // Still exists in DB
            assertThat(deleted.deletedAt).isNotNull(); // But has deletedAt set
            assertThat(deleted.email).isEqualTo("soft-delete@example.com"); // Data preserved
        });
    }

    @Test
    @RunOnVertxContext
    void testDeleteUser_ExcludesFromQueries(UniAsserter asserter) {
        // Given: a user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-exclude", "exclude@example.com", "Exclude Me", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When: deleting the user
        asserter.execute(() ->
            userService.deleteUser(userIdHolder[0])
        );

        // Then: user is not returned by getUserById service method
        asserter.assertFailedWith(() ->
            userService.getUserById(userIdHolder[0])
        , thrown -> {
            assertThat(thrown).isInstanceOf(UserNotFoundException.class);
            assertThat(thrown.getMessage()).contains(userIdHolder[0].toString());
        });

        // And: user is not returned by findByEmail service method
        asserter.assertThat(() ->
            userService.findByEmail("exclude@example.com")
        , found -> {
            assertThat(found).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testDeleteUser_ThrowsExceptionForAlreadyDeleted(UniAsserter asserter) {
        // Given: a deleted user
        UUID[] userIdHolder = new UUID[1];
        asserter.execute(() ->
            userService.createUser("google", "google-double-del", "double-delete@example.com", "Delete Twice", null)
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        asserter.execute(() ->
            userService.deleteUser(userIdHolder[0])
        );

        // When/Then: attempting to delete again throws exception
        asserter.assertFailedWith(() ->
            userService.deleteUser(userIdHolder[0])
        , thrown -> {
            assertThat(thrown).isInstanceOf(UserNotFoundException.class);
            assertThat(thrown.getMessage()).contains(userIdHolder[0].toString());
        });
    }

    // ===== OAuth JIT Provisioning Tests =====

    @Test
    @RunOnVertxContext
    void testFindOrCreateUser_CreatesNewUser(UniAsserter asserter) {
        // Given: OAuth profile for a new user
        String provider = "google";
        String subject = "google-new-jit";
        String email = "jit-new@example.com";
        String displayName = "JIT User";
        String avatarUrl = "https://avatar.com/jit.jpg";

        // When: calling findOrCreateUser
        asserter.assertThat(() ->
            userService.findOrCreateUser(provider, subject, email, displayName, avatarUrl)
        , user -> {
            // Then: new user is created
            assertThat(user).isNotNull();
            assertThat(user.userId).isNotNull();
            assertThat(user.oauthProvider).isEqualTo("google");
            assertThat(user.oauthSubject).isEqualTo("google-new-jit");
            assertThat(user.email).isEqualTo("jit-new@example.com");
            assertThat(user.displayName).isEqualTo("JIT User");
            assertThat(user.avatarUrl).isEqualTo(avatarUrl);
        });

        // And: verify user was persisted
        asserter.assertThat(() -> Panache.withSession(() ->
            userRepository.findByOAuthProviderAndSubject(provider, subject)
        ), found -> {
            assertThat(found).isNotNull();
            assertThat(found.email).isEqualTo("jit-new@example.com");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindOrCreateUser_ReturnsExistingUser(UniAsserter asserter) {
        // Given: an existing user
        String provider = "microsoft";
        String subject = "microsoft-existing";
        UUID[] userIdHolder = new UUID[1];

        asserter.execute(() ->
            userService.createUser(provider, subject, "existing@example.com", "Existing User", "https://old.jpg")
                .onItem().invoke(user -> userIdHolder[0] = user.userId)
        );

        // When: calling findOrCreateUser with same OAuth credentials
        asserter.assertThat(() ->
            userService.findOrCreateUser(provider, subject, "existing@example.com", "Existing User", "https://old.jpg")
        , user -> {
            // Then: existing user is returned (same ID)
            assertThat(user).isNotNull();
            assertThat(user.userId).isEqualTo(userIdHolder[0]);
            assertThat(user.oauthProvider).isEqualTo("microsoft");
            assertThat(user.oauthSubject).isEqualTo("microsoft-existing");
        });

        // And: verify no duplicate was created
        asserter.assertThat(() -> Panache.withSession(() ->
            userRepository.findAll().list()
        ), users -> {
            assertThat(users).hasSize(1);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindOrCreateUser_UpdatesUserProfile(UniAsserter asserter) {
        // Given: an existing user with old profile data
        String provider = "google";
        String subject = "google-update-jit";

        asserter.execute(() ->
            userService.createUser(provider, subject, "old-email@example.com", "Old Name", "https://old-avatar.jpg")
        );

        // When: calling findOrCreateUser with updated profile
        asserter.assertThat(() ->
            userService.findOrCreateUser(provider, subject, "new-email@example.com", "New Name", "https://new-avatar.jpg")
        , user -> {
            // Then: user profile is updated
            assertThat(user).isNotNull();
            assertThat(user.email).isEqualTo("new-email@example.com");
            assertThat(user.displayName).isEqualTo("New Name");
            assertThat(user.avatarUrl).isEqualTo("https://new-avatar.jpg");
        });
    }
}
