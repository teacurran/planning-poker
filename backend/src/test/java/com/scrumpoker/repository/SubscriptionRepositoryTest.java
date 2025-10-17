package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.domain.billing.Subscription;
import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.domain.user.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for SubscriptionRepository.
 * Tests CRUD operations, polymorphic entity references, and subscription queries.
 */
@QuarkusTest
class SubscriptionRepositoryTest {

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    UserRepository userRepository;

    private User testUser;

    @BeforeEach
    @Transactional
    void setUp() {
        subscriptionRepository.deleteAll().await().indefinitely();
        userRepository.deleteAll().await().indefinitely();

        testUser = createTestUser("subuser@example.com", "google", "google-sub");
        userRepository.persist(testUser).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new subscription
        Subscription sub = createTestSubscription(testUser.userId, EntityType.USER);

        // When: persisting the subscription
        subscriptionRepository.persist(sub).await().indefinitely();

        // Then: the subscription can be retrieved
        Subscription found = subscriptionRepository.findById(sub.subscriptionId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.tier).isEqualTo(SubscriptionTier.PRO);
        assertThat(found.status).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @Transactional
    void testFindActiveByEntityIdAndType() {
        // Given: an active subscription
        Subscription sub = createTestSubscription(testUser.userId, EntityType.USER);
        subscriptionRepository.persist(sub).await().indefinitely();

        // When: finding active subscription
        Subscription found = subscriptionRepository.findActiveByEntityIdAndType(testUser.userId, EntityType.USER)
                .await().indefinitely();

        // Then: the active subscription is returned
        assertThat(found).isNotNull();
        assertThat(found.status).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @Transactional
    void testFindByStripeSubscriptionId() {
        // Given: subscription with Stripe ID
        Subscription sub = createTestSubscription(testUser.userId, EntityType.USER);
        subscriptionRepository.persist(sub).await().indefinitely();

        // When: finding by Stripe ID
        Subscription found = subscriptionRepository.findByStripeSubscriptionId("stripe_sub_123")
                .await().indefinitely();

        // Then: the subscription is found
        assertThat(found).isNotNull();
        assertThat(found.stripeSubscriptionId).isEqualTo("stripe_sub_123");
    }

    @Test
    @Transactional
    void testFindByStatus() {
        // Given: subscriptions with different statuses
        Subscription active = createTestSubscription(testUser.userId, EntityType.USER);
        Subscription canceled = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        canceled.status = SubscriptionStatus.CANCELED;

        subscriptionRepository.persist(active).await().indefinitely();
        subscriptionRepository.persist(canceled).await().indefinitely();

        // When: finding active subscriptions
        List<Subscription> activeSubscriptions = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)
                .await().indefinitely();

        // Then: only active subscriptions are returned
        assertThat(activeSubscriptions).hasSize(1);
        assertThat(activeSubscriptions.get(0).status).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    @Transactional
    void testFindByTier() {
        // Given: subscriptions with different tiers
        Subscription pro = createTestSubscription(testUser.userId, EntityType.USER);
        pro.tier = SubscriptionTier.PRO;

        Subscription proPlus = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        proPlus.tier = SubscriptionTier.PRO_PLUS;

        subscriptionRepository.persist(pro).await().indefinitely();
        subscriptionRepository.persist(proPlus).await().indefinitely();

        // When: finding PRO tier subscriptions
        List<Subscription> proSubscriptions = subscriptionRepository.findByTier(SubscriptionTier.PRO)
                .await().indefinitely();

        // Then: only PRO tier subscriptions are returned
        assertThat(proSubscriptions).hasSize(1);
        assertThat(proSubscriptions.get(0).tier).isEqualTo(SubscriptionTier.PRO);
    }

    @Test
    @Transactional
    void testCountActiveByTier() {
        // Given: active subscriptions of different tiers
        Subscription pro1 = createTestSubscription(testUser.userId, EntityType.USER);
        pro1.tier = SubscriptionTier.PRO;

        Subscription pro2 = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        pro2.tier = SubscriptionTier.PRO;

        subscriptionRepository.persist(pro1).await().indefinitely();
        subscriptionRepository.persist(pro2).await().indefinitely();

        // When: counting active PRO subscriptions
        Long count = subscriptionRepository.countActiveByTier(SubscriptionTier.PRO).await().indefinitely();

        // Then: correct count is returned
        assertThat(count).isEqualTo(2);
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

    private Subscription createTestSubscription(UUID entityId, EntityType entityType) {
        Subscription sub = new Subscription();
        sub.subscriptionId = UUID.randomUUID();
        sub.stripeSubscriptionId = "stripe_sub_123";
        sub.entityId = entityId;
        sub.entityType = entityType;
        sub.tier = SubscriptionTier.PRO;
        sub.status = SubscriptionStatus.ACTIVE;
        sub.currentPeriodEnd = Instant.now().plus(30, ChronoUnit.DAYS);
        return sub;
    }
}
