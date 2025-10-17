package com.scrumpoker.repository;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import com.scrumpoker.domain.user.UserPreference;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> preferenceRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given & When: creating and persisting a new user preference
        User testUser = createTestUser("prefuser@example.com", "google", "google-pref");
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).chain(user ->
                preferenceRepository.persist(createTestPreference(user))
            )
        ));

        // Then: the preference can be retrieved by user ID
        asserter.assertThat(() -> Panache.withTransaction(() -> preferenceRepository.findById(testUser.userId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.userId).isEqualTo(testUser.userId);
            assertThat(found.defaultDeckType).isEqualTo("fibonacci");
            assertThat(found.theme).isEqualTo("dark");
        });
    }

    @Test
    @RunOnVertxContext
    void testJsonbDefaultRoomConfig(UniAsserter asserter) {
        // Given: preference with JSONB default room config
        User testUser = createTestUser("prefuser@example.com", "google", "google-pref");
        String jsonConfig = "{\"deckType\":\"tshirt\",\"timerEnabled\":true,\"revealBehavior\":\"manual\"}";

        // When: persisting
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).chain(user -> {
                UserPreference pref = createTestPreference(user);
                pref.defaultRoomConfig = jsonConfig;
                return preferenceRepository.persist(pref);
            })
        ));

        // Then: JSONB field round-trips correctly
        asserter.assertThat(() -> Panache.withTransaction(() -> preferenceRepository.findById(testUser.userId)), found -> {
            assertThat(found.defaultRoomConfig).isEqualTo(jsonConfig);
            assertThat(found.defaultRoomConfig).contains("tshirt");
        });
    }

    @Test
    @RunOnVertxContext
    void testJsonbNotificationSettings(UniAsserter asserter) {
        // Given: preference with JSONB notification settings
        User testUser = createTestUser("prefuser@example.com", "google", "google-pref");
        String jsonSettings = "{\"emailEnabled\":true,\"pushEnabled\":false,\"notificationTypes\":[\"VOTE_CAST\",\"ROUND_COMPLETE\"]}";

        // When: persisting
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).chain(user -> {
                UserPreference pref = createTestPreference(user);
                pref.notificationSettings = jsonSettings;
                return preferenceRepository.persist(pref);
            })
        ));

        // Then: JSONB notification settings persist correctly
        asserter.assertThat(() -> Panache.withTransaction(() -> preferenceRepository.findById(testUser.userId)), found -> {
            assertThat(found.notificationSettings).isEqualTo(jsonSettings);
            assertThat(found.notificationSettings).contains("VOTE_CAST");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByUserId(UniAsserter asserter) {
        // Given: persisted preference
        User testUser = createTestUser("prefuser@example.com", "google", "google-pref");
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).chain(user ->
                preferenceRepository.persist(createTestPreference(user))
            )
        ));

        // When: finding by user ID
        // Then: preference is found
        asserter.assertThat(() -> Panache.withTransaction(() -> preferenceRepository.findByUserId(testUser.userId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.userId).isEqualTo(testUser.userId);
        });
    }

    @Test
    @RunOnVertxContext
    void testCountByTheme(UniAsserter asserter) {
        // Given: multiple preferences with different themes
        User user1 = createTestUser("user1@example.com", "google", "google-1");
        User user2 = createTestUser("user2@example.com", "google", "google-2");
        User testUser = createTestUser("prefuser@example.com", "google", "google-pref");

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user1).chain(u -> {
                UserPreference pref1 = createTestPreference(u);
                pref1.theme = "dark";
                return preferenceRepository.persist(pref1);
            })
        ));

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(user2).chain(u -> {
                UserPreference pref2 = createTestPreference(u);
                pref2.theme = "dark";
                return preferenceRepository.persist(pref2);
            })
        ));

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).chain(u -> {
                UserPreference pref3 = createTestPreference(u);
                pref3.theme = "light";
                return preferenceRepository.persist(pref3);
            })
        ));

        // When: counting by theme
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> preferenceRepository.countByTheme("dark")), darkCount -> {
            assertThat(darkCount).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdatePreference(UniAsserter asserter) {
        // Given: persisted preference
        User testUser = createTestUser("prefuser@example.com", "google", "google-pref");
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).chain(user ->
                preferenceRepository.persist(createTestPreference(user))
            )
        ));

        // When: updating preference
        asserter.execute(() -> Panache.withTransaction(() ->
            preferenceRepository.findById(testUser.userId).flatMap(preference -> {
                preference.theme = "light";
                preference.defaultDeckType = "tshirt";
                return preferenceRepository.persist(preference);
            })
        ));

        // Then: changes are persisted
        asserter.assertThat(() -> Panache.withTransaction(() -> preferenceRepository.findById(testUser.userId)), updated -> {
            assertThat(updated.theme).isEqualTo("light");
            assertThat(updated.defaultDeckType).isEqualTo("tshirt");
        });
    }

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
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
        pref.createdAt = Instant.now();
        pref.updatedAt = Instant.now();
        return pref;
    }
}
