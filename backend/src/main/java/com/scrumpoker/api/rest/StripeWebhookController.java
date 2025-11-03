package com.scrumpoker.api.rest;

import com.scrumpoker.domain.billing.BillingService;
import com.scrumpoker.domain.billing.PaymentHistory;
import com.scrumpoker.domain.billing.PaymentStatus;
import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.billing.WebhookEventLog;
import com.scrumpoker.domain.billing.WebhookEventStatus;
import com.scrumpoker.repository.PaymentHistoryRepository;
import com.scrumpoker.repository.SubscriptionRepository;
import com.scrumpoker.repository.WebhookEventLogRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Invoice;
import com.stripe.net.Webhook;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * REST controller for Stripe webhook events.
 * Handles subscription lifecycle events and payment notifications from Stripe.
 * <p>
 * This endpoint MUST:
 * - Verify webhook signature to prevent unauthorized events
 * - Implement idempotency to handle Stripe retries (up to 3 days)
 * - Always return 200 OK to acknowledge receipt (even on processing failures)
 * - Log errors but not throw exceptions to user
 * </p>
 * <p>
 * Security: Authenticated via Stripe signature verification only (no JWT).
 * Uses @PermitAll since Stripe webhooks don't send JWT tokens.
 * </p>
 */
@Path("/api/v1/subscriptions/webhook")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Billing",
     description = "Stripe webhook endpoints for subscription events")
public class StripeWebhookController {

    /** Logger instance for webhook processing. */
    private static final Logger LOG =
        Logger.getLogger(StripeWebhookController.class);

    /** Domain service for billing operations. */
    @Inject
    private BillingService billingService;

    /** Repository for subscription entities. */
    @Inject
    private SubscriptionRepository subscriptionRepository;

    /** Repository for payment history entities. */
    @Inject
    private PaymentHistoryRepository paymentHistoryRepository;

    /** Repository for webhook event log entities. */
    @Inject
    private WebhookEventLogRepository webhookEventLogRepository;

    /** Stripe webhook signing secret for signature verification. */
    @ConfigProperty(name = "stripe.webhook-secret")
    private String webhookSecret;

    /**
     * POST /api/v1/subscriptions/webhook - Receive Stripe webhooks.
     * <p>
     * Stripe sends webhook events for subscription lifecycle changes
     * and payments. This endpoint verifies the signature, checks
     * idempotency, processes the event, and always returns 200 OK
     * to prevent Stripe retries.
     * </p>
     * <p>
     * Event types handled:
     * - customer.subscription.created: Activate subscription
     * - customer.subscription.updated: Sync status changes
     * - customer.subscription.deleted: Mark as canceled
     * - invoice.payment_succeeded: Create payment history record
     * - invoice.payment_failed: Mark subscription as past due
     * </p>
     *
     * @param payload Raw webhook payload (JSON string)
     * @param signatureHeader Stripe-Signature header for verification
     * @return 200 OK (always) or 401 Unauthorized if signature invalid
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermitAll
    @Operation(
        summary = "Receive Stripe webhook events",
        description = "Webhook endpoint for Stripe subscription "
            + "lifecycle events. Verifies webhook signature, processes "
            + "events idempotently, and always returns 200 OK to "
            + "acknowledge receipt."
    )
    @APIResponse(
        responseCode = "200",
        description = "Webhook event received and processed"
    )
    @APIResponse(
        responseCode = "401",
        description = "Invalid webhook signature"
    )
    public Uni<Response> handleWebhook(
            final String payload,
            @HeaderParam("Stripe-Signature") final String signatureHeader
    ) {

        LOG.debugf(
            "Received Stripe webhook (signature header present: %s)",
            signatureHeader != null);

        // Step 1: Verify webhook signature
        Event event;
        try {
            event = Webhook.constructEvent(
                payload, signatureHeader, webhookSecret);
            LOG.infof(
                "Webhook signature verified for event %s (type: %s)",
                event.getId(), event.getType());
        } catch (SignatureVerificationException e) {
            LOG.errorf(e, "Webhook signature verification failed");
            return Uni.createFrom().item(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\": \"Invalid webhook signature\"}")
                    .build()
            );
        }

        // Step 2: Process event with idempotency and error handling
        return processEventIdempotently(event)
            .onItemOrFailure().transform((ignored, throwable) -> {
                if (throwable != null) {
                    // Log error but return 200 OK to prevent retries
                    LOG.errorf(
                        throwable,
                        "Failed to process webhook event %s (type: %s), "
                        + "returning 200 OK to prevent retries",
                        event.getId(), event.getType());
                } else {
                    LOG.infof(
                        "Successfully processed webhook event %s "
                        + "(type: %s)",
                        event.getId(), event.getType());
                }
                return Response.ok().build();
            });
    }

    /**
     * Processes webhook event with idempotency check.
     * Checks if event was already processed, routes to appropriate handler,
     * and records processing outcome in WebhookEventLog.
     *
     * @param event Verified Stripe event
     * @return Uni<Void> signaling completion
     */
    @Transactional
    protected Uni<Void> processEventIdempotently(final Event event) {
        final String eventId = event.getId();
        final String eventType = event.getType();

        // Step 1: Check idempotency - already processed?
        return webhookEventLogRepository.findByEventId(eventId)
            .onItem().transformToUni(existingLog -> {
                if (existingLog != null) {
                    LOG.infof(
                        "Event %s already processed (status: %s), skipping",
                        eventId, existingLog.status);
                    return Uni.createFrom().voidItem();
                }

                // Step 2: Route to appropriate event handler
                return processEventByType(event)
                    .onItem().transformToUni(ignored -> {
                        // Step 3: Record successful processing
                        WebhookEventLog log = new WebhookEventLog();
                        log.eventId = eventId;
                        log.eventType = eventType;
                        log.status = WebhookEventStatus.PROCESSED;
                        return webhookEventLogRepository.persist(log);
                    })
                    .onFailure().recoverWithUni(throwable -> {
                        // Record failed processing
                        LOG.errorf(
                            throwable,
                            "Event processing failed for %s (type: %s), "
                            + "recording FAILED status",
                            eventId, eventType);

                        WebhookEventLog log = new WebhookEventLog();
                        log.eventId = eventId;
                        log.eventType = eventType;
                        log.status = WebhookEventStatus.FAILED;

                        return webhookEventLogRepository.persist(log)
                            .onItem().transformToUni(ignored ->
                                Uni.createFrom().failure(throwable)
                            );
                    })
                    .replaceWithVoid();
            });
    }

    /**
     * Routes Stripe event to appropriate handler based on event type.
     *
     * @param event Stripe event
     * @return Uni<Void> signaling completion
     */
    private Uni<Void> processEventByType(final Event event) {
        final String eventType = event.getType();

        return switch (eventType) {
            case "customer.subscription.created" ->
                handleSubscriptionCreated(event);

            case "customer.subscription.updated" ->
                handleSubscriptionUpdated(event);

            case "customer.subscription.deleted" ->
                handleSubscriptionDeleted(event);

            case "invoice.payment_succeeded" ->
                handleInvoicePaymentSucceeded(event);

            case "invoice.payment_failed" ->
                handleInvoicePaymentFailed(event);

            default -> {
                LOG.infof("Ignoring unhandled event type: %s", eventType);
                yield Uni.createFrom().voidItem();
            }
        };
    }

    /**
     * Handles customer.subscription.created event.
     * Syncs subscription status to ACTIVE.
     *
     * @param event Stripe event
     * @return Uni<Void> signaling completion
     */
    private Uni<Void> handleSubscriptionCreated(final Event event) {
        com.stripe.model.Subscription subscription =
            (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        if (subscription == null) {
            LOG.errorf("Failed to deserialize subscription from event %s",
                       event.getId());
            return Uni.createFrom().voidItem();
        }

        final String stripeSubId = subscription.getId();
        LOG.infof("Processing subscription created: %s (status: %s)",
                  stripeSubId, subscription.getStatus());

        return billingService.syncSubscriptionStatus(
            stripeSubId,
            SubscriptionStatus.ACTIVE
        );
    }

    /**
     * Handles customer.subscription.updated event.
     * Maps Stripe status to domain status and syncs to database.
     *
     * @param event Stripe event
     * @return Uni<Void> signaling completion
     */
    private Uni<Void> handleSubscriptionUpdated(final Event event) {
        com.stripe.model.Subscription subscription =
            (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        if (subscription == null) {
            LOG.errorf("Failed to deserialize subscription from event %s",
                       event.getId());
            return Uni.createFrom().voidItem();
        }

        final String stripeSubId = subscription.getId();
        final String stripeStatus = subscription.getStatus();
        final SubscriptionStatus domainStatus =
            mapStripeToDomainStatus(stripeStatus);

        LOG.infof(
            "Processing subscription updated: %s "
            + "(Stripe status: %s → Domain status: %s)",
            stripeSubId, stripeStatus, domainStatus);

        return billingService.syncSubscriptionStatus(
            stripeSubId, domainStatus);
    }

    /**
     * Handles customer.subscription.deleted event.
     * Syncs subscription status to CANCELED.
     *
     * @param event Stripe event
     * @return Uni<Void> signaling completion
     */
    private Uni<Void> handleSubscriptionDeleted(final Event event) {
        com.stripe.model.Subscription subscription =
            (com.stripe.model.Subscription) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        if (subscription == null) {
            LOG.errorf("Failed to deserialize subscription from event %s",
                       event.getId());
            return Uni.createFrom().voidItem();
        }

        final String stripeSubId = subscription.getId();
        LOG.infof("Processing subscription deleted: %s", stripeSubId);

        return billingService.syncSubscriptionStatus(
            stripeSubId,
            SubscriptionStatus.CANCELED
        );
    }

    /**
     * Handles invoice.payment_succeeded event.
     * Creates PaymentHistory record with payment details.
     * Implements idempotency by checking for duplicate invoice IDs.
     *
     * @param event Stripe event
     * @return Uni<Void> signaling completion
     */
    private Uni<Void> handleInvoicePaymentSucceeded(final Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
            .getObject()
            .orElse(null);

        if (invoice == null) {
            LOG.errorf(
                "Failed to deserialize invoice from event %s",
                event.getId());
            return Uni.createFrom().voidItem();
        }

        final String invoiceId = invoice.getId();
        final String stripeSubId = invoice.getSubscription();

        // Skip if invoice is not subscription-related
        if (stripeSubId == null) {
            LOG.infof(
                "Skipping invoice %s (not subscription-related)",
                invoiceId);
            return Uni.createFrom().voidItem();
        }

        LOG.infof(
            "Processing payment succeeded: invoice %s, "
            + "subscription %s, amount %d %s",
            invoiceId, stripeSubId,
            invoice.getAmountPaid(), invoice.getCurrency());

        // Step 1: Check for duplicate invoice (idempotency)
        return paymentHistoryRepository.findByStripeInvoiceId(invoiceId)
            .onItem().transformToUni(existingPayment -> {
                if (existingPayment != null) {
                    LOG.infof(
                        "Payment for invoice %s already recorded, "
                        + "skipping",
                        invoiceId);
                    return Uni.createFrom().voidItem();
                }

                // Step 2: Find subscription to link payment
                return subscriptionRepository
                    .findByStripeSubscriptionId(stripeSubId)
                    .onItem().transformToUni(subscription -> {
                        if (subscription == null) {
                            LOG.warnf(
                                "Subscription %s not found for "
                                + "invoice %s, skipping payment record",
                                stripeSubId, invoiceId);
                            return Uni.createFrom().voidItem();
                        }

                        // Step 3: Create PaymentHistory record
                        PaymentHistory payment = new PaymentHistory();
                        payment.subscription = subscription;
                        payment.stripeInvoiceId = invoiceId;
                        payment.amount =
                            invoice.getAmountPaid().intValue();
                        payment.currency =
                            invoice.getCurrency().toUpperCase();
                        payment.status = PaymentStatus.SUCCEEDED;
                        payment.paidAt =
                            invoice.getStatusTransitions()
                                .getPaidAt() != null
                            ? Instant.ofEpochSecond(
                                invoice.getStatusTransitions()
                                    .getPaidAt())
                            : Instant.now();

                        return paymentHistoryRepository.persist(payment)
                            .onItem().invoke(() ->
                                LOG.infof(
                                    "Created payment history record "
                                    + "for invoice %s (amount: %d %s)",
                                    invoiceId, payment.amount,
                                    payment.currency)
                            )
                            .replaceWithVoid();
                    });
            });
    }

    /**
     * Handles invoice.payment_failed event.
     * Syncs subscription status to PAST_DUE.
     *
     * @param event Stripe event
     * @return Uni<Void> signaling completion
     */
    private Uni<Void> handleInvoicePaymentFailed(final Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
            .getObject()
            .orElse(null);

        if (invoice == null) {
            LOG.errorf(
                "Failed to deserialize invoice from event %s",
                event.getId());
            return Uni.createFrom().voidItem();
        }

        final String invoiceId = invoice.getId();
        final String stripeSubId = invoice.getSubscription();

        if (stripeSubId == null) {
            LOG.infof(
                "Skipping invoice %s (not subscription-related)",
                invoiceId);
            return Uni.createFrom().voidItem();
        }

        LOG.infof(
            "Processing payment failed: invoice %s, subscription %s",
            invoiceId, stripeSubId);

        return billingService.syncSubscriptionStatus(
            stripeSubId,
            SubscriptionStatus.PAST_DUE
        );
    }

    /**
     * Maps Stripe subscription status string to domain status enum.
     *
     * @param stripeStatus Stripe subscription status
     * @return Domain SubscriptionStatus enum value
     */
    private SubscriptionStatus mapStripeToDomainStatus(
            final String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "incomplete", "incomplete_expired", "unpaid" ->
                SubscriptionStatus.PAST_DUE;
            default -> {
                LOG.warnf(
                    "Unknown Stripe status '%s', "
                    + "defaulting to PAST_DUE",
                    stripeStatus);
                yield SubscriptionStatus.PAST_DUE;
            }
        };
    }
}
