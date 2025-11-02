package com.scrumpoker.integration.stripe;

/**
 * DTO containing Stripe checkout session details.
 * Returned by createCheckoutSession to provide the session ID and redirect URL.
 */
public record CheckoutSessionResult(
    /**
     * Stripe checkout session ID (cs_...)
     */
    String sessionId,

    /**
     * Stripe-hosted checkout page URL for redirect
     */
    String checkoutUrl
) {
}
