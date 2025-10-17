package com.scrumpoker.repository;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
    @Transactional
    void setUp() {
        // Clean up any existing test data
        userRepository.deleteAll().await().indefinitely();

        // Create a test user
        testUser = createTestUser("test@example.com", "google", "google-123");
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new user
        User user = createTestUser("john@example.com", "github", "github-456");

        // When: persisting the user
        userRepository.persist(user).await().indefinitely();

        // Then: the user can be retrieved by ID
        User found = userRepository.findById(user.userId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.email).isEqualTo("john@example.com");
        assertThat(found.oauthProvider).isEqualTo("github");
        assertThat(found.oauthSubject).isEqualTo("github-456");
        assertThat(found.displayName).isEqualTo("Test User");
        assertThat(found.subscriptionTier).isEqualTo(SubscriptionTier.FREE);
        assertThat(found.createdAt).isNotNull();
        assertThat(found.updatedAt).isNotNull();
        assertThat(found.deletedAt).isNull();
    }

    @Test
    @Transactional
    void testFindByEmail() {
        // Given: a persisted user
        userRepository.persist(testUser).await().indefinitely();

        // When: finding by email
        User found = userRepository.findByEmail("test@example.com").await().indefinitely();

        // Then: the user is found
        assertThat(found).isNotNull();
        assertThat(found.userId).isEqualTo(testUser.userId);
        assertThat(found.email).isEqualTo("test@example.com");
    }

    @Test
    @Transactional
    void testFindByEmailNotFound() {
        // When: searching for non-existent email
        User found = userRepository.findByEmail("nonexistent@example.com").await().indefinitely();

        // Then: null is returned
        assertThat(found).isNull();
    }

    @Test
    @Transactional
    void testFindByOAuthProviderAndSubject() {
        // Given: a persisted user
        userRepository.persist(testUser).await().indefinitely();

        // When: finding by OAuth provider and subject
        User found = userRepository.findByOAuthProviderAndSubject("google", "google-123")
                .await().indefinitely();

        // Then: the user is found
        assertThat(found).isNotNull();
        assertThat(found.userId).isEqualTo(testUser.userId);
        assertThat(found.oauthProvider).isEqualTo("google");
        assertThat(found.oauthSubject).isEqualTo("google-123");
    }

    @Test
    @Transactional
    void testFindByOAuthProviderAndSubjectNotFound() {
        // When: searching for non-existent OAuth credentials
        User found = userRepository.findByOAuthProviderAndSubject("github", "unknown")
                .await().indefinitely();

        // Then: null is returned
        assertThat(found).isNull();
    }

    @Test
    @Transactional
    void testUpdateUser() {
        // Given: a persisted user
        userRepository.persist(testUser).await().indefinitely();

        // When: updating the user
        testUser.displayName = "Updated Name";
        testUser.avatarUrl = "https://example.com/avatar.jpg";
        testUser.subscriptionTier = SubscriptionTier.PRO;
        userRepository.persist(testUser).await().indefinitely();

        // Then: the changes are persisted
        User updated = userRepository.findById(testUser.userId).await().indefinitely();
        assertThat(updated.displayName).isEqualTo("Updated Name");
        assertThat(updated.avatarUrl).isEqualTo("https://example.com/avatar.jpg");
        assertThat(updated.subscriptionTier).isEqualTo(SubscriptionTier.PRO);
    }

    @Test
    @Transactional
    void testSoftDelete() {
        // Given: a persisted user
        userRepository.persist(testUser).await().indefinitely();

        // When: soft deleting the user
        testUser.deletedAt = Instant.now();
        userRepository.persist(testUser).await().indefinitely();

        // Then: the user still exists but has deletedAt set
        User found = userRepository.findById(testUser.userId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.deletedAt).isNotNull();
    }

    @Test
    @Transactional
    void testFindActiveByEmail() {
        // Given: an active user and a soft-deleted user
        User activeUser = createTestUser("active@example.com", "google", "google-active");
        User deletedUser = createTestUser("deleted@example.com", "google", "google-deleted");
        deletedUser.deletedAt = Instant.now();

        userRepository.persist(activeUser).await().indefinitely();
        userRepository.persist(deletedUser).await().indefinitely();

        // When: finding active users by email
        User foundActive = userRepository.findActiveByEmail("active@example.com").await().indefinitely();
        User foundDeleted = userRepository.findActiveByEmail("deleted@example.com").await().indefinitely();

        // Then: only active user is returned
        assertThat(foundActive).isNotNull();
        assertThat(foundActive.email).isEqualTo("active@example.com");
        assertThat(foundDeleted).isNull();
    }

    @Test
    @Transactional
    void testCountActive() {
        // Given: multiple users with some soft-deleted
        User user1 = createTestUser("user1@example.com", "google", "google-1");
        User user2 = createTestUser("user2@example.com", "google", "google-2");
        User user3 = createTestUser("user3@example.com", "google", "google-3");
        user3.deletedAt = Instant.now();

        userRepository.persist(user1).await().indefinitely();
        userRepository.persist(user2).await().indefinitely();
        userRepository.persist(user3).await().indefinitely();

        // When: counting active users
        Long activeCount = userRepository.countActive().await().indefinitely();

        // Then: only non-deleted users are counted
        assertThat(activeCount).isEqualTo(2);
    }

    @Test
    @Transactional
    void testDeleteUser() {
        // Given: a persisted user
        userRepository.persist(testUser).await().indefinitely();
        UUID userId = testUser.userId;

        // When: hard deleting the user
        userRepository.delete(testUser).await().indefinitely();

        // Then: the user no longer exists
        User found = userRepository.findById(userId).await().indefinitely();
        assertThat(found).isNull();
    }

    @Test
    @Transactional
    void testCountAllUsers() {
        // Given: multiple users
        User user1 = createTestUser("count1@example.com", "google", "google-count1");
        User user2 = createTestUser("count2@example.com", "github", "github-count2");

        userRepository.persist(user1).await().indefinitely();
        userRepository.persist(user2).await().indefinitely();

        // When: counting all users
        Long totalCount = userRepository.count().await().indefinitely();

        // Then: all users are counted
        assertThat(totalCount).isEqualTo(2);
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
