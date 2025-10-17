package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.PaymentHistory;
import com.scrumpoker.domain.billing.PaymentStatus;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for PaymentHistory entity.
 * Provides transaction history queries and payment status tracking.
 */
@ApplicationScoped
public class PaymentHistoryRepository implements PanacheRepositoryBase<PaymentHistory, UUID> {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    /**
     * Find all payment records for a subscription, ordered by payment date.
     *
     * @param subscriptionId The subscription ID
     * @return Uni of list of payment history records
     */
    public Uni<List<PaymentHistory>> findBySubscriptionId(UUID subscriptionId) {
        return find("subscription.subscriptionId = ?1 order by paidAt desc", subscriptionId).list();
    }

    /**
     * Find payment by Stripe invoice ID.
     * Used for webhook processing and invoice reconciliation.
     *
     * @param stripeInvoiceId The Stripe invoice ID
     * @return Uni containing the payment record if found, or null if not found
     */
    public Uni<PaymentHistory> findByStripeInvoiceId(String stripeInvoiceId) {
        return find("stripeInvoiceId", stripeInvoiceId).firstResult();
    }

    /**
     * Find payments by status.
     *
     * @param status The payment status
     * @return Uni of list of payments with the specified status
     */
    public Uni<List<PaymentHistory>> findByStatus(PaymentStatus status) {
        return find("status = ?1 order by paidAt desc", status).list();
    }

    /**
     * Find payments within a date range.
     * Useful for financial reporting and reconciliation.
     *
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni of list of payments within the date range
     */
    public Uni<List<PaymentHistory>> findByDateRange(Instant startDate, Instant endDate) {
        return find("paidAt >= ?1 and paidAt <= ?2 order by paidAt desc",
                    startDate, endDate).list();
    }

    /**
     * Find successful payments for a subscription.
     *
     * @param subscriptionId The subscription ID
     * @return Uni of list of successful payments
     */
    public Uni<List<PaymentHistory>> findSuccessfulBySubscriptionId(UUID subscriptionId) {
        return find("subscription.subscriptionId = ?1 and status = ?2 order by paidAt desc",
                    subscriptionId, PaymentStatus.SUCCEEDED).list();
    }

    /**
     * Count total payments for a subscription.
     *
     * @param subscriptionId The subscription ID
     * @return Uni containing the payment count
     */
    public Uni<Long> countBySubscriptionId(UUID subscriptionId) {
        return count("subscription.subscriptionId", subscriptionId);
    }

    /**
     * Calculate total revenue from successful payments.
     * Note: This returns the sum as Long (cents). Convert to decimal for display.
     * Uses Hibernate Reactive SessionFactory for SUM aggregation.
     *
     * @return Uni containing the total amount in cents
     */
    public Uni<Long> calculateTotalRevenue() {
        return sessionFactory.withSession(session ->
            session.createQuery(
                "SELECT COALESCE(SUM(p.amount), 0) FROM PaymentHistory p WHERE p.status = :status",
                Long.class)
                .setParameter("status", PaymentStatus.SUCCEEDED)
                .getSingleResult()
        );
    }
}
