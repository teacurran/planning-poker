package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.*;
import com.scrumpoker.domain.user.SubscriptionTier;
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
 * Integration tests for PaymentHistoryRepository.
 * Tests CRUD operations and payment tracking queries.
 */
@QuarkusTest
class PaymentHistoryRepositoryTest {

    @Inject
    PaymentHistoryRepository paymentRepository;

    @Inject
    SubscriptionRepository subscriptionRepository;

    private Subscription testSubscription;

    @BeforeEach
    @Transactional
    void setUp() {
        paymentRepository.deleteAll().await().indefinitely();
        subscriptionRepository.deleteAll().await().indefinitely();

        testSubscription = createTestSubscription();
        subscriptionRepository.persist(testSubscription).await().indefinitely();
    }

    @Test
    @Transactional
    void testPersistAndFindById() {
        // Given: a new payment
        PaymentHistory payment = createTestPayment(testSubscription, 1999);

        // When: persisting the payment
        paymentRepository.persist(payment).await().indefinitely();

        // Then: the payment can be retrieved
        PaymentHistory found = paymentRepository.findById(payment.paymentId).await().indefinitely();
        assertThat(found).isNotNull();
        assertThat(found.amount).isEqualTo(1999);
        assertThat(found.status).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    @Test
    @Transactional
    void testFindBySubscriptionId() {
        // Given: multiple payments for a subscription
        paymentRepository.persist(createTestPayment(testSubscription, 1999)).await().indefinitely();
        paymentRepository.persist(createTestPayment(testSubscription, 1999)).await().indefinitely();

        // When: finding payments by subscription ID
        List<PaymentHistory> payments = paymentRepository.findBySubscriptionId(testSubscription.subscriptionId)
                .await().indefinitely();

        // Then: all payments are returned
        assertThat(payments).hasSize(2);
    }

    @Test
    @Transactional
    void testFindByStripeInvoiceId() {
        // Given: payment with Stripe invoice ID
        PaymentHistory payment = createTestPayment(testSubscription, 1999);
        paymentRepository.persist(payment).await().indefinitely();

        // When: finding by Stripe invoice ID
        PaymentHistory found = paymentRepository.findByStripeInvoiceId("stripe_inv_123")
                .await().indefinitely();

        // Then: the payment is found
        assertThat(found).isNotNull();
        assertThat(found.stripeInvoiceId).isEqualTo("stripe_inv_123");
    }

    @Test
    @Transactional
    void testFindByStatus() {
        // Given: payments with different statuses
        PaymentHistory succeeded = createTestPayment(testSubscription, 1999);
        succeeded.status = PaymentStatus.SUCCEEDED;

        PaymentHistory failed = createTestPayment(testSubscription, 1999);
        failed.status = PaymentStatus.FAILED;
        failed.stripeInvoiceId = "stripe_inv_456";

        paymentRepository.persist(succeeded).await().indefinitely();
        paymentRepository.persist(failed).await().indefinitely();

        // When: finding succeeded payments
        List<PaymentHistory> succeededPayments = paymentRepository.findByStatus(PaymentStatus.SUCCEEDED)
                .await().indefinitely();

        // Then: only succeeded payments are returned
        assertThat(succeededPayments).hasSize(1);
    }

    @Test
    @Transactional
    void testCountBySubscriptionId() {
        // Given: multiple payments
        paymentRepository.persist(createTestPayment(testSubscription, 1999)).await().indefinitely();
        paymentRepository.persist(createTestPayment(testSubscription, 1999)).await().indefinitely();

        // When: counting payments
        Long count = paymentRepository.countBySubscriptionId(testSubscription.subscriptionId)
                .await().indefinitely();

        // Then: correct count is returned
        assertThat(count).isEqualTo(2);
    }

    @Test
    @Transactional
    void testCalculateTotalRevenue() {
        // Given: successful payments
        paymentRepository.persist(createTestPayment(testSubscription, 1999)).await().indefinitely();
        paymentRepository.persist(createTestPayment(testSubscription, 2999)).await().indefinitely();

        // When: calculating total revenue
        Long totalRevenue = paymentRepository.calculateTotalRevenue().await().indefinitely();

        // Then: correct sum is returned
        assertThat(totalRevenue).isEqualTo(4998L);
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
