package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.PaymentHistory;
import com.scrumpoker.domain.billing.PaymentStatus;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
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
public class PaymentHistoryRepository
    implements PanacheRepositoryBase<PaymentHistory, UUID> {

    /** Hibernate Reactive session factory for custom queries. */
    @Inject
    private Mutiny.SessionFactory sessionFactory;

    /**
     * Find all payment records for a subscription.
     *
     * @param subscriptionId The subscription ID
     * @return Uni of list of payment history records
     */
    public Uni<List<PaymentHistory>> findBySubscriptionId(
            final UUID subscriptionId) {
        return find("subscription.subscriptionId = ?1 "
            + "order by paidAt desc", subscriptionId).list();
    }

    /**
     * Find payment by Stripe invoice ID.
     * Used for webhook processing and invoice reconciliation.
     *
     * @param stripeInvoiceId The Stripe invoice ID
     * @return Uni containing payment record if found, or null
     */
    public Uni<PaymentHistory> findByStripeInvoiceId(
            final String stripeInvoiceId) {
        return find("stripeInvoiceId", stripeInvoiceId).firstResult();
    }

    /**
     * Find payments by status.
     *
     * @param status The payment status
     * @return Uni of list of payments with the specified status
     */
    public Uni<List<PaymentHistory>> findByStatus(
            final PaymentStatus status) {
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
    public Uni<List<PaymentHistory>> findByDateRange(
            final Instant startDate,
            final Instant endDate) {
        return find("paidAt >= ?1 and paidAt <= ?2 "
            + "order by paidAt desc", startDate, endDate).list();
    }

    /**
     * Find successful payments for a subscription.
     *
     * @param subscriptionId The subscription ID
     * @return Uni of list of successful payments
     */
    public Uni<List<PaymentHistory>> findSuccessfulBySubscriptionId(
            final UUID subscriptionId) {
        return find("subscription.subscriptionId = ?1 "
            + "and status = ?2 order by paidAt desc",
            subscriptionId, PaymentStatus.SUCCEEDED).list();
    }

    /**
     * Count total payments for a subscription.
     *
     * @param subscriptionId The subscription ID
     * @return Uni containing the payment count
     */
    public Uni<Long> countBySubscriptionId(final UUID subscriptionId) {
        return count("subscription.subscriptionId", subscriptionId);
    }

    /**
     * Find all payment records for a user.
     * Used for invoice list endpoint.
     *
     * @param userId The user ID (entityId in subscription)
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Uni of list of payment history records for user
     */
    public Uni<List<PaymentHistory>> findByUserId(
            final UUID userId,
            final int page,
            final int size) {
        return sessionFactory.withSession(session ->
            session.createQuery(
                "SELECT p FROM PaymentHistory p "
                    + "WHERE p.subscription.entityId = :userId "
                    + "AND p.subscription.entityType = 'USER' "
                    + "ORDER BY p.paidAt DESC",
                PaymentHistory.class)
                .setParameter("userId", userId)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList()
        );
    }

    /**
     * Count total payment records for a user.
     * Used for pagination metadata in invoice list endpoint.
     *
     * @param userId The user ID (entityId in subscription)
     * @return Uni containing the count of payments for the user
     */
    public Uni<Long> countByUserId(final UUID userId) {
        return sessionFactory.withSession(session ->
            session.createQuery(
                "SELECT COUNT(p) FROM PaymentHistory p "
                    + "WHERE p.subscription.entityId = :userId "
                    + "AND p.subscription.entityType = 'USER'",
                Long.class)
                .setParameter("userId", userId)
                .getSingleResult()
        );
    }

    /**
     * Calculate total revenue from successful payments.
     * Note: This returns sum as Long (cents).
     * Uses Hibernate Reactive SessionFactory for SUM aggregation.
     *
     * @return Uni containing the total amount in cents
     */
    public Uni<Long> calculateTotalRevenue() {
        return sessionFactory.withSession(session ->
            session.createQuery(
                "SELECT COALESCE(SUM(p.amount), 0) "
                    + "FROM PaymentHistory p "
                    + "WHERE p.status = :status",
                Long.class)
                .setParameter("status", PaymentStatus.SUCCEEDED)
                .getSingleResult()
        );
    }
}
