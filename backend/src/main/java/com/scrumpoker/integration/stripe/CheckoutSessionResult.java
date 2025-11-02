package com.scrumpoker.integration.stripe;

/**
 * DTO containing Stripe checkout session details.
 * Returned by createCheckoutSession to provide the session ID and redirect URL.
 *
 * @param sessionId Stripe checkout session ID (cs_...)
 * @param checkoutUrl Stripe-hosted checkout page URL for redirect
 */
public record CheckoutSessionResult(
    String sessionId,
    String checkoutUrl
) {
}
