package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.NotificationSettingsDTO;
import com.scrumpoker.api.rest.dto.RoomConfigDTO;
import com.scrumpoker.api.rest.dto.UpdateProfileRequest;
import com.scrumpoker.api.rest.dto.UpdateUserPreferenceRequest;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.domain.user.UserPreference;
import com.scrumpoker.repository.UserPreferenceRepository;
import com.scrumpoker.repository.UserRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserController REST endpoints.
 * Tests end-to-end functionality: HTTP request → controller → service → repository → database → response.
 * Uses @QuarkusTest with Testcontainers PostgreSQL for full integration testing.
 */
@QuarkusTest
@TestProfile(NoSecurityTestProfile.class)
public class UserControllerTest {

    @Inject
    UserRepository userRepository;

    @Inject
    UserPreferenceRepository userPreferenceRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up test data before each test
        asserter.execute(() -> Panache.withTransaction(() -> userPreferenceRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));
    }

    // ========================================
    // GET /api/v1/users/{userId} - Get User Profile Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testGetUserProfile_ExistingUser_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("testuser@example.com", "google", "google-123");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Retrieve user profile
        asserter.execute(() ->
            given()
            .when()
                .get("/api/v1/users/" + testUser.userId)
            .then()
                .statusCode(200)
                .body("userId", equalTo(testUser.userId.toString()))
                .body("email", equalTo("testuser@example.com"))
                .body("displayName", equalTo("Test User"))
                .body("subscriptionTier", equalTo("FREE"))
                .body("createdAt", notNullValue())
        );
    }

    @Test
    public void testGetUserProfile_UserNotFound_Returns404() {
        UUID nonExistentUserId = UUID.randomUUID();

        given()
        .when()
            .get("/api/v1/users/" + nonExistentUserId)
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue())
            .body("timestamp", notNullValue());
    }

    // ========================================
    // PUT /api/v1/users/{userId} - Update User Profile Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testUpdateUserProfile_ValidInput_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("updateuser@example.com", "google", "google-update");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Update profile
        UpdateProfileRequest updateRequest = new UpdateProfileRequest();
        updateRequest.displayName = "Updated Name";
        updateRequest.avatarUrl = "https://example.com/avatar.png";

        asserter.execute(() ->
            given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
            .when()
                .put("/api/v1/users/" + testUser.userId)
            .then()
                .statusCode(200)
                .body("userId", equalTo(testUser.userId.toString()))
                .body("displayName", equalTo("Updated Name"))
                .body("avatarUrl", equalTo("https://example.com/avatar.png"))
                .body("email", equalTo("updateuser@example.com"))
        );

        // Verify changes persisted in database
        asserter.assertThat(() -> Panache.withTransaction(() ->
            userRepository.findById(testUser.userId)
        ), user -> {
            assertThat(user).isNotNull();
            assertThat(user.displayName).isEqualTo("Updated Name");
            assertThat(user.avatarUrl).isEqualTo("https://example.com/avatar.png");
        });
    }

    @Test
    @RunOnVertxContext
    public void testUpdateUserProfile_DisplayNameOnly_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("displayonly@example.com", "google", "google-display");
        testUser.avatarUrl = "https://example.com/original.png";

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Update only display name
        UpdateProfileRequest updateRequest = new UpdateProfileRequest();
        updateRequest.displayName = "New Display Name";
        // avatarUrl is not set

        asserter.execute(() ->
            given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
            .when()
                .put("/api/v1/users/" + testUser.userId)
            .then()
                .statusCode(200)
                .body("displayName", equalTo("New Display Name"))
                .body("avatarUrl", equalTo("https://example.com/original.png")) // Should remain unchanged
        );
    }

    @Test
    @RunOnVertxContext
    public void testUpdateUserProfile_AvatarUrlOnly_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("avataronly@example.com", "google", "google-avatar");
        testUser.displayName = "Original Name";

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Update only avatar URL
        UpdateProfileRequest updateRequest = new UpdateProfileRequest();
        updateRequest.avatarUrl = "https://example.com/new-avatar.jpg";
        // displayName is not set

        asserter.execute(() ->
            given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
            .when()
                .put("/api/v1/users/" + testUser.userId)
            .then()
                .statusCode(200)
                .body("displayName", equalTo("Original Name")) // Should remain unchanged
                .body("avatarUrl", equalTo("https://example.com/new-avatar.jpg"))
        );
    }

    @Test
    @RunOnVertxContext
    public void testUpdateUserProfile_DisplayNameTooLong_Returns400(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("longname@example.com", "google", "google-long");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Try to update with display name > 100 characters
        UpdateProfileRequest updateRequest = new UpdateProfileRequest();
        updateRequest.displayName = "A".repeat(101); // 101 characters

        asserter.execute(() ->
            given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
            .when()
                .put("/api/v1/users/" + testUser.userId)
            .then()
                .statusCode(400)
        );
    }

    @Test
    public void testUpdateUserProfile_UserNotFound_Returns404() {
        UUID nonExistentUserId = UUID.randomUUID();

        UpdateProfileRequest updateRequest = new UpdateProfileRequest();
        updateRequest.displayName = "New Name";

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
        .when()
            .put("/api/v1/users/" + nonExistentUserId)
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue());
    }

    // ========================================
    // GET /api/v1/users/{userId}/preferences - Get Preferences Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testGetUserPreferences_ExistingPreferences_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("prefuser@example.com", "google", "google-pref");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Create preferences for the user (fetch user within same transaction to avoid detached entity)
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.findById(testUser.userId).flatMap(user -> {
                UserPreference preference = createTestPreference(user);
                return userPreferenceRepository.persist(preference);
            })
        ));

        // Get preferences
        asserter.execute(() ->
            given()
            .when()
                .get("/api/v1/users/" + testUser.userId + "/preferences")
            .then()
                .statusCode(200)
                .body("userId", equalTo(testUser.userId.toString()))
                .body("defaultDeckType", equalTo("fibonacci"))
                .body("theme", equalTo("dark"))
                .body("notificationSettings", notNullValue())
                .body("defaultRoomConfig", notNullValue())
        );
    }

    @Test
    @RunOnVertxContext
    public void testGetUserPreferences_UserNotFound_Returns404(UniAsserter asserter) {
        UUID nonExistentUserId = UUID.randomUUID();

        // Try to get preferences for non-existent user
        given()
        .when()
            .get("/api/v1/users/" + nonExistentUserId + "/preferences")
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue());
    }

    @Test
    @RunOnVertxContext
    public void testGetUserPreferences_NoPreferencesYet_Returns200WithDefaults(UniAsserter asserter) {
        // Create a test user without preferences
        User testUser = createTestUser("nopref@example.com", "google", "google-nopref");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Get preferences - should return defaults (created by UserService)
        asserter.execute(() ->
            given()
            .when()
                .get("/api/v1/users/" + testUser.userId + "/preferences")
            .then()
                .statusCode(200)
                .body("userId", equalTo(testUser.userId.toString()))
                .body("defaultDeckType", notNullValue())
        );
    }

    // ========================================
    // PUT /api/v1/users/{userId}/preferences - Update Preferences Tests
    // ========================================

    @Test
    @RunOnVertxContext
    public void testUpdateUserPreferences_AllFields_Returns200(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("updatepref@example.com", "google", "google-updatepref");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Create initial preferences (fetch user within transaction)
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.findById(testUser.userId).flatMap(user -> {
                UserPreference preference = createTestPreference(user);
                return userPreferenceRepository.persist(preference);
            })
        ));

        // Update preferences with all fields
        UpdateUserPreferenceRequest updateRequest = new UpdateUserPreferenceRequest();
        updateRequest.defaultDeckType = "tshirt";
        updateRequest.theme = "light";

        NotificationSettingsDTO notifSettings = new NotificationSettingsDTO();
        notifSettings.emailNotifications = false;
        notifSettings.sessionReminders = true;
        updateRequest.notificationSettings = notifSettings;

        RoomConfigDTO roomConfig = new RoomConfigDTO();
        roomConfig.deckType = "powers_of_2";
        roomConfig.timerEnabled = true;
        roomConfig.timerDurationSeconds = 180;
        roomConfig.allowObservers = false;
        updateRequest.defaultRoomConfig = roomConfig;

        asserter.execute(() ->
            given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
            .when()
                .put("/api/v1/users/" + testUser.userId + "/preferences")
            .then()
                .statusCode(200)
                .body("userId", equalTo(testUser.userId.toString()))
                .body("defaultDeckType", equalTo("tshirt"))
                .body("theme", equalTo("light"))
                .body("notificationSettings.emailNotifications", equalTo(false))
                .body("notificationSettings.sessionReminders", equalTo(true))
                .body("defaultRoomConfig.deckType", equalTo("powers_of_2"))
                .body("defaultRoomConfig.timerEnabled", equalTo(true))
                .body("defaultRoomConfig.timerDurationSeconds", equalTo(180))
        );
    }

    @Test
    @RunOnVertxContext
    public void testUpdateUserPreferences_JsonbPersistence_VerifiedInDatabase(UniAsserter asserter) {
        // Create a test user
        User testUser = createTestUser("jsonbtest@example.com", "google", "google-jsonb");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        // Create initial preferences (fetch user within transaction)
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.findById(testUser.userId).flatMap(user -> {
                UserPreference preference = createTestPreference(user);
                return userPreferenceRepository.persist(preference);
            })
        ));

        // Update with complex nested JSONB structures
        UpdateUserPreferenceRequest updateRequest = new UpdateUserPreferenceRequest();

        NotificationSettingsDTO notifSettings = new NotificationSettingsDTO();
        notifSettings.emailNotifications = true;
        notifSettings.sessionReminders = true;
        updateRequest.notificationSettings = notifSettings;

        RoomConfigDTO roomConfig = new RoomConfigDTO();
        roomConfig.deckType = "custom";
        roomConfig.customDeck = java.util.List.of("1", "2", "3", "5", "8", "13", "?");
        roomConfig.timerEnabled = true;
        roomConfig.timerDurationSeconds = 300;
        roomConfig.revealBehavior = "automatic";
        roomConfig.allowObservers = true;
        roomConfig.allowAnonymousVoters = false;
        updateRequest.defaultRoomConfig = roomConfig;

        asserter.execute(() ->
            given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
            .when()
                .put("/api/v1/users/" + testUser.userId + "/preferences")
            .then()
                .statusCode(200)
        );

        // Verify JSONB fields persisted correctly in database
        asserter.assertThat(() -> Panache.withTransaction(() ->
            userPreferenceRepository.findByUserId(testUser.userId)
        ), pref -> {
            assertThat(pref).isNotNull();
            assertThat(pref.notificationSettings).isNotNull();
            assertThat(pref.notificationSettings).contains("emailNotifications");
            assertThat(pref.notificationSettings).contains("true");
            assertThat(pref.defaultRoomConfig).isNotNull();
            assertThat(pref.defaultRoomConfig).contains("custom");
            assertThat(pref.defaultRoomConfig).contains("customDeck");
            assertThat(pref.defaultRoomConfig).contains("automatic");
        });

        // Verify by retrieving via API again
        asserter.execute(() ->
            given()
            .when()
                .get("/api/v1/users/" + testUser.userId + "/preferences")
            .then()
                .statusCode(200)
                .body("notificationSettings.emailNotifications", equalTo(true))
                .body("notificationSettings.sessionReminders", equalTo(true))
                .body("defaultRoomConfig.deckType", equalTo("custom"))
                .body("defaultRoomConfig.customDeck", hasSize(7))
                .body("defaultRoomConfig.revealBehavior", equalTo("automatic"))
        );
    }

    @Test
    @RunOnVertxContext
    public void testUpdateUserPreferences_PartialUpdate_Returns200(UniAsserter asserter) {
        // Create a test user with existing preferences
        User testUser = createTestUser("partial@example.com", "google", "google-partial");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser)
        ));

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.findById(testUser.userId).flatMap(user -> {
                UserPreference preference = createTestPreference(user);
                return userPreferenceRepository.persist(preference);
            })
        ));

        // Update only theme
        UpdateUserPreferenceRequest updateRequest = new UpdateUserPreferenceRequest();
        updateRequest.theme = "system";

        asserter.execute(() ->
            given()
                .contentType(ContentType.JSON)
                .body(updateRequest)
            .when()
                .put("/api/v1/users/" + testUser.userId + "/preferences")
            .then()
                .statusCode(200)
                .body("theme", equalTo("system"))
                .body("defaultDeckType", equalTo("fibonacci")) // Should remain unchanged
        );
    }

    @Test
    public void testUpdateUserPreferences_UserNotFound_Returns404() {
        UUID nonExistentUserId = UUID.randomUUID();

        UpdateUserPreferenceRequest updateRequest = new UpdateUserPreferenceRequest();
        updateRequest.theme = "light";

        given()
            .contentType(ContentType.JSON)
            .body(updateRequest)
        .when()
            .put("/api/v1/users/" + nonExistentUserId + "/preferences")
        .then()
            .statusCode(404)
            .body("error", notNullValue())
            .body("message", notNullValue());
    }

    // ========================================
    // Authorization Tests (Future - Iteration 3)
    // ========================================

    // NOTE: Authorization (403 Forbidden) tests are not implemented yet.
    // These will be added in Iteration 3 when JWT authentication is implemented.
    // Expected behavior:
    // - Users should only be able to update their own profile (403 if accessing another user's profile)
    // - Users should only be able to view/update their own preferences (403 for other users)

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Helper method to create test users.
     * User ID is auto-generated by Hibernate on persist.
     */
    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    /**
     * Helper method to create test user preferences.
     */
    private UserPreference createTestPreference(User user) {
        UserPreference preference = new UserPreference();
        preference.user = user;
        preference.defaultDeckType = "fibonacci";
        preference.theme = "dark";
        preference.notificationSettings = "{\"emailNotifications\":true,\"sessionReminders\":true}";
        preference.defaultRoomConfig = "{\"deckType\":\"fibonacci\",\"timerEnabled\":false,\"allowObservers\":true}";
        preference.createdAt = Instant.now();
        preference.updatedAt = Instant.now();
        return preference;
    }
}
