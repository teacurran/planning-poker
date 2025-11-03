package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.WebhookEventLog;
import com.scrumpoker.domain.billing.WebhookEventStatus;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;

/**
 * Reactive Panache repository for WebhookEventLog entity.
 * Provides idempotency checks and event processing audit.
 */
@ApplicationScoped
public class WebhookEventLogRepository
        implements PanacheRepositoryBase<WebhookEventLog, String> {

    /**
     * Find event log by Stripe event ID.
     * Used for idempotency check before processing webhook events.
     *
     * @param eventId The Stripe event ID
     * @return Uni containing the event log if found, or null if not found
     */
    public Uni<WebhookEventLog> findByEventId(final String eventId) {
        return findById(eventId);
    }

    /**
     * Find all events processed within a date range.
     * Useful for audit and cleanup operations.
     *
     * @param startDate The start of the date range
     * @param endDate The end of the date range
     * @return Uni of list of webhook event logs
     */
    public Uni<List<WebhookEventLog>> findByDateRange(
            final Instant startDate,
            final Instant endDate) {
        return find(
            "processedAt >= ?1 and processedAt <= ?2 "
            + "order by processedAt desc",
            startDate, endDate).list();
    }

    /**
     * Find events by status.
     *
     * @param status The webhook event status
     * @return Uni of list of webhook event logs
     */
    public Uni<List<WebhookEventLog>> findByStatus(
            final WebhookEventStatus status) {
        return find(
            "status = ?1 order by processedAt desc",
            status).list();
    }

    /**
     * Find events by event type.
     * Useful for analyzing specific webhook event processing.
     *
     * @param eventType Stripe event type
     * @return Uni of list of webhook event logs
     */
    public Uni<List<WebhookEventLog>> findByEventType(
            final String eventType) {
        return find(
            "eventType = ?1 order by processedAt desc",
            eventType).list();
    }

    /**
     * Count failed events for monitoring.
     *
     * @return Uni containing the count of failed events
     */
    public Uni<Long> countFailed() {
        return count("status", WebhookEventStatus.FAILED);
    }
}
