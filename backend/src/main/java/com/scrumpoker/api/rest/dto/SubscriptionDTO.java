package com.scrumpoker.api.rest.dto;

import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a subscription for REST API responses.
 * Maps to the Subscription entity and OpenAPI SubscriptionDTO schema.
 */
@SuppressWarnings({"checkstyle:visibilitymodifier", "PMD"})
public class SubscriptionDTO {

    /**
     * Subscription unique identifier.
     */
    public UUID subscriptionId;

    /**
     * Stripe subscription ID (e.g., sub_1MqjMq2eZvKYlo2C...).
     */
    public String stripeSubscriptionId;

    /**
     * User or organization ID.
     */
    public UUID entityId;

    /**
     * Entity type (USER or ORG).
     */
    public EntityType entityType;

    /**
     * Subscription tier (FREE, PRO, PRO_PLUS, ENTERPRISE).
     */
    public SubscriptionTier tier;

    /**
     * Subscription status (ACTIVE, PAST_DUE, CANCELED, TRIALING).
     */
    public SubscriptionStatus status;

    /**
     * Current billing period start timestamp.
     */
    public Instant currentPeriodStart;

    /**
     * Current billing period end timestamp.
     */
    public Instant currentPeriodEnd;

    /**
     * Cancellation timestamp (null if active).
     */
    public Instant canceledAt;

    /**
     * Creation timestamp.
     */
    public Instant createdAt;
}
