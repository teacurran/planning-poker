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
        // Given: a new user and subscription
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription sub = createTestSubscription(null, EntityType.USER);
        final UUID[] subIdHolder = new UUID[1];

        // When: persisting the user and subscription
        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> {
                sub.entityId = user.userId;
                return subscriptionRepository.persist(sub).map(s -> {
                    subIdHolder[0] = s.subscriptionId;
                    return s;
                });
            })
        ));

        // Then: the subscription can be retrieved
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findById(subIdHolder[0])), found -> {
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
        Subscription sub = createTestSubscription(null, EntityType.USER);
        final UUID[] userIdHolder = new UUID[1];

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> {
                sub.entityId = user.userId;
                userIdHolder[0] = user.userId;
                return subscriptionRepository.persist(sub);
            })
        ));

        // When: finding active subscription
        // Then: the active subscription is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findActiveByEntityIdAndType(userIdHolder[0], EntityType.USER)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.status).isEqualTo(SubscriptionStatus.ACTIVE);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByStripeSubscriptionId(UniAsserter asserter) {
        // Given: subscription with Stripe ID
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription sub = createTestSubscription(null, EntityType.USER);
        final String[] stripeIdHolder = new String[1];

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> {
                sub.entityId = user.userId;
                stripeIdHolder[0] = sub.stripeSubscriptionId; // Capture the generated Stripe ID
                return subscriptionRepository.persist(sub);
            })
        ));

        // When: finding by Stripe ID
        // Then: the subscription is found
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.findByStripeSubscriptionId(stripeIdHolder[0])), found -> {
            assertThat(found).isNotNull();
            assertThat(found.stripeSubscriptionId).isEqualTo(stripeIdHolder[0]);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByStatus(UniAsserter asserter) {
        // Given: subscriptions with different statuses
        User testUser = createTestUser("subuser@example.com", "google", "google-sub");
        Subscription active = createTestSubscription(null, EntityType.USER);
        Subscription canceled = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        canceled.status = SubscriptionStatus.CANCELED;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> {
                active.entityId = user.userId;
                return subscriptionRepository.persist(active).flatMap(s1 ->
                    subscriptionRepository.persist(canceled)
                );
            })
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
        Subscription pro = createTestSubscription(null, EntityType.USER);
        pro.tier = SubscriptionTier.PRO;

        Subscription proPlus = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        proPlus.tier = SubscriptionTier.PRO_PLUS;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> {
                pro.entityId = user.userId;
                return subscriptionRepository.persist(pro).flatMap(s1 ->
                    subscriptionRepository.persist(proPlus)
                );
            })
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
        Subscription pro1 = createTestSubscription(null, EntityType.USER);
        pro1.tier = SubscriptionTier.PRO;

        Subscription pro2 = createTestSubscription(UUID.randomUUID(), EntityType.USER);
        pro2.tier = SubscriptionTier.PRO;

        asserter.execute(() -> Panache.withTransaction(() ->
            userRepository.persist(testUser).flatMap(user -> {
                pro1.entityId = user.userId;
                return subscriptionRepository.persist(pro1).flatMap(s1 ->
                    subscriptionRepository.persist(pro2)
                );
            })
        ));

        // When: counting active PRO subscriptions
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> subscriptionRepository.countActiveByTier(SubscriptionTier.PRO)), count -> {
            assertThat(count).isEqualTo(2);
        });
    }

    private User createTestUser(String email, String provider, String subject) {
        User user = new User();
        // DO NOT SET user.userId - let Hibernate auto-generate it
        user.email = email;
        user.oauthProvider = provider;
        user.oauthSubject = subject;
        user.displayName = "Test User";
        user.subscriptionTier = SubscriptionTier.FREE;
        return user;
    }

    private Subscription createTestSubscription(UUID entityId, EntityType entityType) {
        Subscription sub = new Subscription();
        // DO NOT SET sub.subscriptionId - let Hibernate auto-generate it
        sub.stripeSubscriptionId = "stripe_sub_" + UUID.randomUUID().toString().substring(0, 8); // Unique Stripe ID
        sub.entityId = entityId;
        sub.entityType = entityType;
        sub.tier = SubscriptionTier.PRO;
        sub.status = SubscriptionStatus.ACTIVE;
        sub.currentPeriodStart = Instant.now();
        sub.currentPeriodEnd = Instant.now().plus(30, ChronoUnit.DAYS);
        sub.createdAt = Instant.now();
        sub.updatedAt = Instant.now();
        return sub;
    }
}
