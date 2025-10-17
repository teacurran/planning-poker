package com.scrumpoker.repository;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
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
 * Integration tests for UserRepository.
 * Tests CRUD operations, custom finders, and soft delete behavior using Testcontainers PostgreSQL.
 */
@QuarkusTest
class UserRepositoryTest {

    @Inject
    UserRepository userRepository;

    private User testUser;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        // Clean up any existing test data
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));

        // Create a test user
        testUser = createTestUser("test@example.com", "google", "google-123");
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new user
        User user = createTestUser("john@example.com", "github", "github-456");

        // When: persisting the user
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.persist(user)));

        // Then: the user can be retrieved by ID
        asserter.assertThat(() -> Panache.withTransaction(() -> userRepository.findById(user.userId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.email).isEqualTo("john@example.com");
            assertThat(found.oauthProvider).isEqualTo("github");
            assertThat(found.oauthSubject).isEqualTo("github-456");
            assertThat(found.displayName).isEqualTo("Test User");
            assertThat(found.subscriptionTier).isEqualTo(SubscriptionTier.FREE);
            assertThat(found.createdAt).isNotNull();
            assertThat(found.updatedAt).isNotNull();
            assertThat(found.deletedAt).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByEmail(UniAsserter asserter) {
        // Given: a persisted user
        asserter.execute(() -> userRepository.persist(testUser));

        // When: finding by email
        // Then: the user is found
        asserter.assertThat(() -> userRepository.findByEmail("test@example.com"), found -> {
            assertThat(found).isNotNull();
            assertThat(found.userId).isEqualTo(testUser.userId);
            assertThat(found.email).isEqualTo("test@example.com");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByEmailNotFound(UniAsserter asserter) {
        // When: searching for non-existent email
        // Then: null is returned
        asserter.assertThat(() -> userRepository.findByEmail("nonexistent@example.com"), found -> {
            assertThat(found).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByOAuthProviderAndSubject(UniAsserter asserter) {
        // Given: a persisted user
        asserter.execute(() -> userRepository.persist(testUser));

        // When: finding by OAuth provider and subject
        // Then: the user is found
        asserter.assertThat(() -> userRepository.findByOAuthProviderAndSubject("google", "google-123"), found -> {
            assertThat(found).isNotNull();
            assertThat(found.userId).isEqualTo(testUser.userId);
            assertThat(found.oauthProvider).isEqualTo("google");
            assertThat(found.oauthSubject).isEqualTo("google-123");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByOAuthProviderAndSubjectNotFound(UniAsserter asserter) {
        // When: searching for non-existent OAuth credentials
        // Then: null is returned
        asserter.assertThat(() -> userRepository.findByOAuthProviderAndSubject("github", "unknown"), found -> {
            assertThat(found).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testUpdateUser(UniAsserter asserter) {
        // Given: a persisted user
        asserter.execute(() -> userRepository.persist(testUser));

        // When: updating the user
        asserter.execute(() -> {
            testUser.displayName = "Updated Name";
            testUser.avatarUrl = "https://example.com/avatar.jpg";
            testUser.subscriptionTier = SubscriptionTier.PRO;
            return userRepository.persist(testUser);
        });

        // Then: the changes are persisted
        asserter.assertThat(() -> userRepository.findById(testUser.userId), updated -> {
            assertThat(updated.displayName).isEqualTo("Updated Name");
            assertThat(updated.avatarUrl).isEqualTo("https://example.com/avatar.jpg");
            assertThat(updated.subscriptionTier).isEqualTo(SubscriptionTier.PRO);
        });
    }

    @Test
    @RunOnVertxContext
    void testSoftDelete(UniAsserter asserter) {
        // Given: a persisted user
        asserter.execute(() -> userRepository.persist(testUser));

        // When: soft deleting the user
        asserter.execute(() -> {
            testUser.deletedAt = Instant.now();
            return userRepository.persist(testUser);
        });

        // Then: the user still exists but has deletedAt set
        asserter.assertThat(() -> userRepository.findById(testUser.userId), found -> {
            assertThat(found).isNotNull();
            assertThat(found.deletedAt).isNotNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testFindActiveByEmail(UniAsserter asserter) {
        // Given: an active user and a soft-deleted user
        User activeUser = createTestUser("active@example.com", "google", "google-active");
        User deletedUser = createTestUser("deleted@example.com", "google", "google-deleted");
        deletedUser.deletedAt = Instant.now();

        asserter.execute(() -> userRepository.persist(activeUser));
        asserter.execute(() -> userRepository.persist(deletedUser));

        // When: finding active users by email
        // Then: only active user is returned
        asserter.assertThat(() -> userRepository.findActiveByEmail("active@example.com"), foundActive -> {
            assertThat(foundActive).isNotNull();
            assertThat(foundActive.email).isEqualTo("active@example.com");
        });

        asserter.assertThat(() -> userRepository.findActiveByEmail("deleted@example.com"), foundDeleted -> {
            assertThat(foundDeleted).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testCountActive(UniAsserter asserter) {
        // Given: multiple users with some soft-deleted
        User user1 = createTestUser("user1@example.com", "google", "google-1");
        User user2 = createTestUser("user2@example.com", "google", "google-2");
        User user3 = createTestUser("user3@example.com", "google", "google-3");
        user3.deletedAt = Instant.now();

        asserter.execute(() -> userRepository.persist(user1));
        asserter.execute(() -> userRepository.persist(user2));
        asserter.execute(() -> userRepository.persist(user3));

        // When: counting active users
        // Then: only non-deleted users are counted
        asserter.assertThat(() -> userRepository.countActive(), activeCount -> {
            assertThat(activeCount).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testDeleteUser(UniAsserter asserter) {
        // Given: a persisted user
        asserter.execute(() -> userRepository.persist(testUser));
        UUID userId = testUser.userId;

        // When: hard deleting the user
        asserter.execute(() -> userRepository.delete(testUser));

        // Then: the user no longer exists
        asserter.assertThat(() -> userRepository.findById(userId), found -> {
            assertThat(found).isNull();
        });
    }

    @Test
    @RunOnVertxContext
    void testCountAllUsers(UniAsserter asserter) {
        // Given: multiple users
        User user1 = createTestUser("count1@example.com", "google", "google-count1");
        User user2 = createTestUser("count2@example.com", "github", "github-count2");

        asserter.execute(() -> userRepository.persist(user1));
        asserter.execute(() -> userRepository.persist(user2));

        // When: counting all users
        // Then: all users are counted
        asserter.assertThat(() -> userRepository.count(), totalCount -> {
            assertThat(totalCount).isEqualTo(2);
        });
    }

    /**
     * Helper method to create test users with unique IDs.
     */
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
}
