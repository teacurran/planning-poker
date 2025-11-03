package com.scrumpoker.api.rest.dto;

import com.scrumpoker.domain.billing.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * DTO representing a payment history record for REST API responses.
 * Maps to the PaymentHistory entity and OpenAPI PaymentHistoryDTO schema.
 */
public class PaymentHistoryDTO {

    /**
     * Payment unique identifier.
     */
    public UUID paymentId;

    /**
     * Associated subscription ID.
     */
    public UUID subscriptionId;

    /**
     * Stripe invoice ID (e.g., in_1MqjMq2eZvKYlo2C...).
     */
    public String stripeInvoiceId;

    /**
     * Amount in cents (e.g., 2999 = $29.99).
     */
    public Integer amount;

    /**
     * ISO 4217 currency code (e.g., "USD").
     */
    public String currency;

    /**
     * Payment status (SUCCEEDED, PENDING, FAILED).
     */
    public PaymentStatus status;

    /**
     * Payment timestamp.
     */
    public Instant paidAt;
}
