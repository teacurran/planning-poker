package com.scrumpoker.integration.stripe;

import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;

import java.time.Instant;

/**
 * DTO containing Stripe subscription details.
 * Maps Stripe subscription object to domain model fields.
 */
public record StripeSubscriptionInfo(
    /**
     * Stripe subscription ID (sub_...)
     */
    String subscriptionId,

    /**
     * Stripe customer ID (cus_...)
     */
    String customerId,

    /**
     * Subscription tier (derived from Stripe price ID)
     */
    SubscriptionTier tier,

    /**
     * Subscription status
     */
    SubscriptionStatus status,

    /**
     * Current billing period start timestamp
     */
    Instant currentPeriodStart,

    /**
     * Current billing period end timestamp
     */
    Instant currentPeriodEnd,

    /**
     * Cancellation timestamp (null if not canceled)
     */
    Instant canceledAt
) {
}
