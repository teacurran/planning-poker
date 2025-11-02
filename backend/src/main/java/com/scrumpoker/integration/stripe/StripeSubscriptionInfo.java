package com.scrumpoker.integration.stripe;

import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;

import java.time.Instant;

/**
 * DTO containing Stripe subscription details.
 * Maps Stripe subscription object to domain model fields.
 *
 * @param subscriptionId Stripe subscription ID (sub_...)
 * @param customerId Stripe customer ID (cus_...)
 * @param tier Subscription tier (derived from Stripe price ID)
 * @param status Subscription status
 * @param currentPeriodStart Current billing period start timestamp
 * @param currentPeriodEnd Current billing period end timestamp
 * @param canceledAt Cancellation timestamp (null if not canceled)
 */
public record StripeSubscriptionInfo(
    String subscriptionId,
    String customerId,
    SubscriptionTier tier,
    SubscriptionStatus status,
    Instant currentPeriodStart,
    Instant currentPeriodEnd,
    Instant canceledAt
) {
}
