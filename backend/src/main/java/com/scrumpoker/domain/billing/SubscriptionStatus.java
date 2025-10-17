package com.scrumpoker.domain.billing;

/**
 * Subscription status matching database subscription_status_enum.
 */
public enum SubscriptionStatus {
    ACTIVE,
    PAST_DUE,
    CANCELED,
    TRIALING
}
