package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.domain.billing.Subscription;
import com.scrumpoker.domain.billing.SubscriptionStatus;
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
import java.time.temporal.ChronoUnit;
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

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> subscriptionRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> userRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a new subscription
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription sub = createTestSubscription(testUser.userId, EntityType.USER);

        // When: persisting the user and subscription
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> subscriptionRepository.persist(sub))
        ));

        // Then: the subscription can be retrieved
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findById(sub.subscriptionId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.tier).isEqualTo(SubscriptionTier.PRO);
            assertThat(found.status).isEqualTo(SubscriptionStatus.ACTIVE);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindActiveByEntityIdAndType(UniAsserter asserter) {
        // Given: an active subscription
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription sub = createTestSubscription(testUser.userId, EntityType.USER);
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> subscriptionRepository.persist(sub))
        ));

        // When: finding active subscription
        // Then: the active subscription is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findActiveByEntityIdAndType(testUser.userId, EntityType.USER)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.status).isEqualTo(SubscriptionStatus.ACTIVE);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByStripeSubscriptionId(UniAsserter asserter) {
        // Given: subscription with Stripe ID
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription sub = createTestSubscription(testUser.userId, EntityType.USER);
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> subscriptionRepository.persist(sub))
        ));

        // When: finding by Stripe ID
        // Then: the subscription is found
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findByStripeSubscriptionId("stripe_sub_123")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.stripeSubscriptionId).isEqualTo("stripe_sub_123");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByStatus(UniAsserter asserter) {
        // Given: subscriptions with different statuses
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription active = createTestSubscription(testUser.userId, EntityType.USER);
        Subscription canceled = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        canceled.status = SubscriptionStatus.CANCELED;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                subscriptionRepository.persist(active).flatMap(s1 ->
                    subscriptionRepository.persist(canceled)
                )
            )
        ));

        // When: finding active subscriptions
        // Then: only active subscriptions are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)), activeSubscriptions -> {
            assertThat(activeSubscriptions).hasSize(1);
            assertThat(activeSubscriptions.get(0).status).isEqualTo(SubscriptionStatus.ACTIVE);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByTier(UniAsserter asserter) {
        // Given: subscriptions with different tiers
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription pro = createTestSubscription(testUser.userId, EntityType.USER);
        pro.tier = SubscriptionTier.PRO;

        Subscription proPlus = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        proPlus.tier = SubscriptionTier.PRO_PLUS;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                subscriptionRepository.persist(pro).flatMap(s1 ->
                    subscriptionRepository.persist(proPlus)
                )
            )
        ));

        // When: finding PRO tier subscriptions
        // Then: only PRO tier subscriptions are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findByTier(SubscriptionTier.PRO)), proSubscriptions -> {
            assertThat(proSubscriptions).hasSize(1);
            assertThat(proSubscriptions.get(0).tier).isEqualTo(SubscriptionTier.PRO);
        });
    }

    @Test
    @RunOnVertxContext
    void testCountActiveByTier(UniAsserter asserter) {
        // Given: active subscriptions of different tiers
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription pro1 = createTestSubscription(testUser.userId, EntityType.USER);
        pro1.tier = SubscriptionTier.PRO;

        Subscription pro2 = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        pro2.tier = SubscriptionTier.PRO;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user ->
                subscriptionRepository.persist(pro1).flatMap(s1 ->
                    subscriptionRepository.persist(pro2)
                )
            )
        ));

        // When: counting active PRO subscriptions
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.countActiveByTier(SubscriptionTier.PRO)), count -> {
            assertThat(count).isEqualTo(2);
        });
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
