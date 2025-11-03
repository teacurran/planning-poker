package com.scrumpoker.domain.billing;

/**
 * Webhook event processing status matching database webhook_event_status_enum.
 */
public enum WebhookEventStatus {
    /**
     * Event was successfully processed and all business logic completed.
     */
    PROCESSED,

    /**
     * Event processing failed (exception thrown).
     * Error logged, but 200 OK returned to Stripe to prevent retries.
     */
    FAILED
}
