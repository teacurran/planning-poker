package com.scrumpoker.api.rest.dto;

import com.scrumpoker.domain.user.SubscriptionTier;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a Stripe checkout session.
 * Maps to OpenAPI CheckoutRequest schema.
 */
// CHECKSTYLE:OFF VisibilityModifier - DTOs use public fields for Jackson
public class CreateCheckoutRequest {

    /**
     * Target subscription tier (PRO or PRO_PLUS only).
     */
    @NotNull
    public SubscriptionTier tier;

    /**
     * Redirect URL on successful payment.
     */
    @NotNull
    public String successUrl;

    /**
     * Redirect URL on canceled payment.
     */
    @NotNull
    public String cancelUrl;
}
// CHECKSTYLE:ON VisibilityModifier
