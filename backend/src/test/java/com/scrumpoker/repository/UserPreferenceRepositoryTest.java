package com.scrumpoker.repository;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.domain.user.UserPreference;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for UserPreferenceRepository.
 * Tests CRUD operations, JSONB field serialization, and 1:1 relationship with User.
 */
@QuarkusTest
class UserPreferenceRepositoryTest {

    @Inject
    UserPreferenceRepository preferenceRepository;

    @Inject
    UserRepository userRepository;

    private User testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        preferenceRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();

        testUser = createTestUser("prefuser@example.com", "google", "google-pref");
        userRepository.persist(testUser).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new user preference
        UserPreference pref = createTestPreference(testUser);

        // When: persisting the preference
        preferenceRepository.persist(pref).await().indefinitely();

        // Then: the preference can be retrieved by user ID
        UserPreference found = preferenceRepository.findById(testUser.userId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.userId).isEqualTo(testUser.userId);
        assertThat(found.defaultDeckType).isEqualTo("fibonacci");
        assertThat(found.theme).isEqualTo("dark");
    }

    @Test
    @Transactional
    void testJsonbDefaultRoomConfig() {
        // Given: preference with JSONB default room config
        UserPreference pref = createTestPreference(testUser);
        String jsonConfig = "{\"deckType\":\"tshirt\",\"timerEnabled\":true,\"revealBehavior\":\"manual\"}";
        pref.defaultRoomConfig = jsonConfig;

        // When: persisting and retrieving
        preferenceRepository.persist(pref).await().indefinitely();
        UserPreference found = preferenceRepository.findById(testUser.userId).await().indefinitely();

        // Then: JSONB field round-trips correctly
        assertThat(found.defaultRoomConfig).isEqualTo(jsonConfig);
        assertThat(found.defaultRoomConfig).contains("tshirt");
    }

    @Test
    @Transactional
    void testJsonbNotificationSettings() {
        // Given: preference with JSONB notification settings
        UserPreference pref = createTestPreference(testUser);
        String jsonSettings = "{\"emailEnabled\":true,\"pushEnabled\":false,\"notificationTypes\":[\"VOTE_CAST\",\"ROUND_COMPLETE\"]}";
        pref.notificationSettings = jsonSettings;

        // When: persisting and retrieving
        preferenceRepository.persist(pref).await().indefinitely();
        UserPreference found = preferenceRepository.findById(testUser.userId).await().indefinitely();

        // Then: JSONB notification settings persist correctly
        assertThat(found.notificationSettings).isEqualTo(jsonSettings);
        assertThat(found.notificationSettings).contains("VOTE_CAST");
    }

    @Test
    @Transactional
    void testFindByUserId() {
        // Given: persisted preference
        UserPreference pref = createTestPreference(testUser);
        preferenceRepository.persist(pref).await().indefinitely();

        // When: finding by user ID
        UserPreference found = preferenceRepository.findByUserId(testUser.userId).await().indefinitely();

        // Then: preference is found
        assertThat(found).isNotNull();
        assertThat(found.userId).isEqualTo(testUser.userId);
    }

    @Test
    @Transactional
    void testCountByTheme() {
        // Given: multiple preferences with different themes
        User user1 = createTestUser("user1@example.com", "google", "google-1");
        User user2 = createTestUser("user2@example.com", "google", "google-2");
        userRepository.persist(user1).await().indefinitely();
        userRepository.persist(user2).await().indefinitely();

        UserPreference pref1 = createTestPreference(user1);
        pref1.theme = "dark";
        UserPreference pref2 = createTestPreference(user2);
        pref2.theme = "dark";
        UserPreference pref3 = createTestPreference(testUser);
        pref3.theme = "light";

        preferenceRepository.persist(pref1).await().indefinitely();
        preferenceRepository.persist(pref2).await().indefinitely();
        preferenceRepository.persist(pref3).await().indefinitely();

        // When: counting by theme
        Long darkCount = preferenceRepository.countByTheme("dark").await().indefinitely();

        // Then: correct count is returned
        assertThat(darkCount).isEqualTo(2);
    }

    @Test
    @Transactional
    void testUpdatePreference() {
        // Given: persisted preference
        UserPreference pref = createTestPreference(testUser);
        preferenceRepository.persist(pref).await().indefinitely();

        // When: updating preference
        pref.theme = "light";
        pref.defaultDeckType = "tshirt";
        preferenceRepository.persist(pref).await().indefinitely();

        // Then: changes are persisted
        UserPreference updated = preferenceRepository.findById(testUser.userId).await().indefinitely();
        assertThat(updated.theme).isEqualTo("light");
        assertThat(updated.defaultDeckType).isEqualTo("tshirt");
    }

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        user.userId = UUID.randomUUID();
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    private UserPreference createTestPreference(User user) {
        UserPreference pref = new UserPreference();
        pref.userId = user.userId;
        pref.user = user;
        pref.defaultDeckType = "fibonacci";
        pref.theme = "dark";
        return pref;
    }
}
