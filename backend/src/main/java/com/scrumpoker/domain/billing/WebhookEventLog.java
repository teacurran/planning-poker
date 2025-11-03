package com.scrumpoker.domain.billing;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Idempotency log for Stripe webhook events.
 * Stores processed event IDs to prevent duplicate processing on webhook retries.
 * <p>
 * Stripe retries failed webhooks for up to 3 days, so idempotency is critical
 * to prevent duplicate subscription/payment updates.
 * </p>
 */
@Entity
@Table(name = "webhook_event_log", uniqueConstraints = {
    @UniqueConstraint(name = "uq_webhook_event_id", columnNames = "event_id")
})
public class WebhookEventLog extends PanacheEntityBase {

    /**
     * Stripe event ID (e.g., "evt_1234567890abcdefghijklmnop").
     * Primary key for natural idempotency.
     */
    @Id
    @NotNull
    @Size(max = 100)
    @Column(name = "event_id", nullable = false, length = 100)
    public String eventId;

    /**
     * Stripe event type (e.g., "customer.subscription.created").
     */
    @NotNull
    @Size(max = 100)
    @Column(name = "event_type", nullable = false, length = 100)
    public String eventType;

    /**
     * Timestamp when event was processed.
     * Used for cleanup queries and audit trail.
     */
    @NotNull
    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    public Instant processedAt;

    /**
     * Processing outcome status.
     * PROCESSED = successfully processed
     * FAILED = processing failed (logged error, returned 200 to Stripe)
     */
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "webhook_event_status_enum")
    public WebhookEventStatus status;
}
