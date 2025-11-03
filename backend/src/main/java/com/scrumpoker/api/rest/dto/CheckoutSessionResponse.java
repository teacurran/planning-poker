package com.scrumpoker.api.rest.dto;

/**
 * Response DTO containing Stripe checkout session information.
 * Maps to OpenAPI CheckoutResponse schema.
 */
public class CheckoutSessionResponse {

    /**
     * Stripe Checkout session ID (e.g., cs_test_a1...).
     */
    public String sessionId;

    /**
     * Stripe Checkout URL for redirect.
     */
    public String checkoutUrl;

    /**
     * Default constructor for Jackson deserialization.
     */
    public CheckoutSessionResponse() {
    }

    /**
     * Constructor with all fields.
     *
     * @param sessionId Stripe session ID
     * @param checkoutUrl Stripe checkout URL
     */
    public CheckoutSessionResponse(String sessionId, String checkoutUrl) {
        this.sessionId = sessionId;
        this.checkoutUrl = checkoutUrl;
    }
}
