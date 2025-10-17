package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.*;
import com.scrumpoker.domain.user.SubscriptionTier;
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
 * Integration tests for PaymentHistoryRepository.
 * Tests CRUD operations and payment tracking queries.
 */
@QuarkusTest
class PaymentHistoryRepositoryTest {

    @Inject
    PaymentHistoryRepository paymentRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @BeforeEach
    @RunOnVertxContext
    void setUp(UniAsserter asserter) {
        asserter.execute(() -> Panache.withTransaction(() -> paymentRepository.deleteAll()));
        asserter.execute(() -> Panache.withTransaction(() -> subscriptionRepository.deleteAll()));
    }

    @Test
    @RunOnVertxContext
    void testPersistAndFindById(UniAsserter asserter) {
        // Given: a subscription and payment
        Subscription testSubscription = createTestSubscription();
        PaymentHistory payment = createTestPayment(testSubscription, 1999);

        // When: persisting both
        asserter.execute(() -> Panache.withTransaction(() ->
            subscriptionRepository.persist(testSubscription).flatMap(sub ->
                paymentRepository.persist(payment)
            )
        ));

        // Then: the payment can be retrieved
        asserter.assertThat(() -> Panache.withTransaction(() -> paymentRepository.findById(payment.paymentId)), found -> {
            assertThat(found).isNotNull();
            assertThat(found.amount).isEqualTo(1999);
            assertThat(found.status).isEqualTo(PaymentStatus.SUCCEEDED);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindBySubscriptionId(UniAsserter asserter) {
        // Given: multiple payments for a subscription
        Subscription testSubscription = createTestSubscription();

        asserter.execute(() -> Panache.withTransaction(() ->
            subscriptionRepository.persist(testSubscription).flatMap(sub -> {
                PaymentHistory p1 = createTestPayment(sub, 1999);
                PaymentHistory p2 = createTestPayment(sub, 1999);
                p2.paymentId = UUID.randomUUID(); // Ensure unique IDs
                p2.stripeInvoiceId = "stripe_inv_124";
                return paymentRepository.persist(p1).flatMap(payment1 ->
                    paymentRepository.persist(p2)
                );
            })
        ));

        // When: finding payments by subscription ID
        // Then: all payments are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> paymentRepository.findBySubscriptionId(testSubscription.subscriptionId)), payments -> {
            assertThat(payments).hasSize(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByStripeInvoiceId(UniAsserter asserter) {
        // Given: payment with Stripe invoice ID
        Subscription testSubscription = createTestSubscription();
        PaymentHistory payment = createTestPayment(testSubscription, 1999);

        asserter.execute(() -> Panache.withTransaction(() ->
            subscriptionRepository.persist(testSubscription).flatMap(sub ->
                paymentRepository.persist(payment)
            )
        ));

        // When: finding by Stripe invoice ID
        // Then: the payment is found
        asserter.assertThat(() -> Panache.withTransaction(() -> paymentRepository.findByStripeInvoiceId("stripe_inv_123")), found -> {
            assertThat(found).isNotNull();
            assertThat(found.stripeInvoiceId).isEqualTo("stripe_inv_123");
        });
    }

    @Test
    @RunOnVertxContext
    void testFindByStatus(UniAsserter asserter) {
        // Given: payments with different statuses
        Subscription testSubscription = createTestSubscription();
        PaymentHistory succeeded = createTestPayment(testSubscription, 1999);
        succeeded.status = PaymentStatus.SUCCEEDED;

        PaymentHistory failed = createTestPayment(testSubscription, 1999);
        failed.status = PaymentStatus.FAILED;
        failed.paymentId = UUID.randomUUID(); // Ensure unique ID
        failed.stripeInvoiceId = "stripe_inv_456";

        asserter.execute(() -> Panache.withTransaction(() ->
            subscriptionRepository.persist(testSubscription).flatMap(sub ->
                paymentRepository.persist(succeeded).flatMap(p1 ->
                    paymentRepository.persist(failed)
                )
            )
        ));

        // When: finding succeeded payments
        // Then: only succeeded payments are returned
        asserter.assertThat(() -> Panache.withTransaction(() -> paymentRepository.findByStatus(PaymentStatus.SUCCEEDED)), succeededPayments -> {
            assertThat(succeededPayments).hasSize(1);
        });
    }

    @Test
    @RunOnVertxContext
    void testCountBySubscriptionId(UniAsserter asserter) {
        // Given: multiple payments
        Subscription testSubscription = createTestSubscription();

        asserter.execute(() -> Panache.withTransaction(() ->
            subscriptionRepository.persist(testSubscription).flatMap(sub -> {
                PaymentHistory p1 = createTestPayment(sub, 1999);
                PaymentHistory p2 = createTestPayment(sub, 1999);
                p2.paymentId = UUID.randomUUID(); // Ensure unique IDs
                p2.stripeInvoiceId = "stripe_inv_125";
                return paymentRepository.persist(p1).flatMap(payment1 ->
                    paymentRepository.persist(p2)
                );
            })
        ));

        // When: counting payments
        // Then: correct count is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> paymentRepository.countBySubscriptionId(testSubscription.subscriptionId)), count -> {
            assertThat(count).isEqualTo(2);
        });
    }

    @Test
    @RunOnVertxContext
    void testCalculateTotalRevenue(UniAsserter asserter) {
        // Given: successful payments
        Subscription testSubscription = createTestSubscription();

        asserter.execute(() -> Panache.withTransaction(() ->
            subscriptionRepository.persist(testSubscription).flatMap(sub -> {
                PaymentHistory p1 = createTestPayment(sub, 1999);
                PaymentHistory p2 = createTestPayment(sub, 2999);
                p2.paymentId = UUID.randomUUID(); // Ensure unique IDs
                p2.stripeInvoiceId = "stripe_inv_126";
                return paymentRepository.persist(p1).flatMap(payment1 ->
                    paymentRepository.persist(p2)
                );
            })
        ));

        // When: calculating total revenue
        // Then: correct sum is returned
        asserter.assertThat(() -> Panache.withTransaction(() -> paymentRepository.calculateTotalRevenue()), totalRevenue -> {
            assertThat(totalRevenue).isEqualTo(4998L);
        });
    }

    private Subscription createTestSubscription() {
        Subscription sub = new Subscription();
        sub.subscriptionId = UUID.randomUUID();
        sub.stripeSubscriptionId = "stripe_sub_test";
        sub.entityId = UUID.randomUUID();
        sub.entityType = EntityType.USER;
        sub.tier = SubscriptionTier.PRO;
        sub.status = SubscriptionStatus.ACTIVE;
        sub.currentPeriodEnd = Instant.now().plus(30, ChronoUnit.DAYS);
        return sub;
    }

    private PaymentHistory createTestPayment(Subscription subscription, Integer amount) {
        PaymentHistory payment = new PaymentHistory();
        payment.paymentId = UUID.randomUUID();
        payment.subscription = subscription;
        payment.stripeInvoiceId = "stripe_inv_123";
        payment.amount = amount;
        payment.currency = "usd";
        payment.status = PaymentStatus.SUCCEEDED;
        payment.paidAt = Instant.now();
        return payment;
    }
}
