package com.scrumpoker.integration.stripe;

/**
 * Custom exception for Stripe integration errors.
 * Wraps checked Stripe SDK exceptions into unchecked exceptions
 * to maintain reactive call chains and domain exception handling patterns.
 */
public class StripeException extends RuntimeException {

    /**
     * Creates a new StripeException with the specified message.
     *
     * @param message The error message
     */
    public StripeException(String message) {
        super(message);
    }

    /**
     * Creates a new StripeException with the specified message and cause.
     *
     * @param message The error message
     * @param cause The underlying cause (typically com.stripe.exception.StripeException)
     */
    public StripeException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new StripeException wrapping a Stripe SDK exception.
     *
     * @param cause The Stripe SDK exception
     */
    public StripeException(Throwable cause) {
        super("Stripe API error: " + cause.getMessage(), cause);
    }
}
