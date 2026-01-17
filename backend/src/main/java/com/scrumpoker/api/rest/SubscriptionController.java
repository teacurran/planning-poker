package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.CheckoutSessionResponse;
import com.scrumpoker.api.rest.dto.CreateCheckoutRequest;
import com.scrumpoker.api.rest.dto.ErrorResponse;
import com.scrumpoker.api.rest.dto.InvoiceListResponse;
import com.scrumpoker.api.rest.dto.PaymentHistoryDTO;
import com.scrumpoker.api.rest.dto.SubscriptionDTO;
import com.scrumpoker.api.rest.mapper.PaymentHistoryMapper;
import com.scrumpoker.api.rest.mapper.SubscriptionMapper;
import com.scrumpoker.domain.billing.BillingService;
import com.scrumpoker.integration.stripe.CheckoutSessionResult;
import com.scrumpoker.integration.stripe.StripeAdapter;
import com.scrumpoker.repository.PaymentHistoryRepository;
import com.scrumpoker.repository.SubscriptionRepository;
import com.scrumpoker.security.SecurityContextImpl;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for subscription and billing management operations.
 * <p>
 * Provides endpoints for:
 * - Retrieving current subscription status
 * - Creating Stripe checkout sessions for subscription upgrades
 * - Canceling subscriptions (soft cancel at period end)
 * - Listing payment history/invoices
 * </p>
 * <p>
 * Security: All endpoints require JWT authentication. Users can only access
 * their own subscription data and payment history (owner-only enforcement).
 * </p>
 * <p>
 * This controller integrates with:
 * - BillingService for subscription lifecycle management
 * - StripeAdapter for payment processing and checkout sessions
 * - PaymentHistoryRepository for invoice queries
 * </p>
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Subscriptions",
     description = "Subscription and billing management endpoints")
public class SubscriptionController {

    /** Logger for subscription controller operations. */
    private static final Logger LOG =
        Logger.getLogger(SubscriptionController.class);

    /** Maximum page size for invoice list pagination. */
    private static final int MAX_PAGE_SIZE = 100;

    /** Billing service for subscription lifecycle management. */
    @Inject
    private BillingService billingService;

    /** Stripe adapter for payment processing integration. */
    @Inject
    private StripeAdapter stripeAdapter;

    /** Repository for subscription persistence. */
    @Inject
    private SubscriptionRepository subscriptionRepository;

    /** Repository for payment history persistence. */
    @Inject
    private PaymentHistoryRepository paymentHistoryRepository;

    /** Mapper for subscription entity-to-DTO conversion. */
    @Inject
    private SubscriptionMapper subscriptionMapper;

    /** Mapper for payment history entity-to-DTO conversion. */
    @Inject
    private PaymentHistoryMapper paymentHistoryMapper;

    /** Security context for authentication and authorization. */
    @Inject
    private SecurityContextImpl securityContext;

    /**
     * GET /api/v1/subscriptions/{userId} - Get current subscription status.
     * <p>
     * Returns the user's current subscription tier, billing status, and
     * feature limits. For FREE tier users (no subscription record), returns
     * a synthetic SubscriptionDTO with FREE tier defaults.
     * </p>
     * <p>
     * Authorization: Users can only access their own subscription.
     * Attempting to access another user's subscription returns 403 Forbidden.
     * </p>
     *
     * @param userIdString User ID path parameter (UUID format)
     * @return 200 OK with SubscriptionDTO
     * @throws WebApplicationException 400 Bad Request if UUID invalid,
     *     401 Unauthorized if not authenticated,
     *     403 Forbidden if accessing other user's data,
     *     500 Internal Server Error on failure
     */
    @GET
    @Path("/subscriptions/{userId}")
    @RolesAllowed("USER")
    @Operation(summary = "Get current subscription status",
        description = "Returns current subscription tier, billing "
            + "status, and feature limits.")
    @APIResponse(responseCode = "200",
        description = "Subscription retrieved",
        content = @Content(
            schema = @Schema(implementation = SubscriptionDTO.class)))
    @APIResponse(responseCode = "400",
        description = "Invalid UUID format",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401",
        description = "Unauthorized",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403",
        description = "Forbidden - user accessing another user's "
            + "subscription",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404",
        description = "User not found",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500",
        description = "Internal server error",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getSubscription(
            @Parameter(description = "User UUID", required = true)
            @PathParam("userId") final String userIdString) {

        UUID currentUserId = requireCurrentUserId();

        // Parse and validate userId
        UUID requestedUserId;
        try {
            requestedUserId = UUID.fromString(userIdString);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Invalid UUID format for userId: %s", userIdString);
            return Uni.createFrom().item(
                badRequest("Invalid UUID format for userId"));
        }

        // Authorization check: user can only access own subscription
        if (!requestedUserId.equals(currentUserId)) {
            LOG.warnf(
                "User %s attempted to access subscription for user %s",
                currentUserId,
                requestedUserId
            );
            return Uni.createFrom().item(
                forbiddenResponse("User can only access own subscription"));
        }

        LOG.infof(
            "GET /subscriptions/%s - requested by user %s",
            requestedUserId,
            currentUserId
        );

        // Fetch active subscription
        return billingService.getActiveSubscription(requestedUserId)
            .onItem().transform(subscription -> {
                SubscriptionDTO dto;
                if (subscription == null) {
                    // FREE tier user (no subscription record)
                    LOG.debugf(
                        "User %s has no subscription, returning FREE tier DTO",
                        requestedUserId
                    );
                    dto = subscriptionMapper.createFreeTierDTO(requestedUserId);
                } else {
                    // Paid tier user (subscription exists)
                    LOG.debugf(
                        "User %s has subscription %s (tier: %s)",
                        requestedUserId,
                        subscription.subscriptionId,
                        subscription.tier
                    );
                    dto = subscriptionMapper.toDTO(subscription);
                }
                return Response.ok(dto).build();
            })
            .onFailure().invoke(throwable ->
                LOG.errorf(throwable,
                    "Failed to fetch subscription for user %s",
                    requestedUserId)
            );
    }

    /**
     * POST /api/v1/subscriptions/checkout - Create Stripe checkout session.
     * <p>
     * Creates a Stripe Checkout session for upgrading to PRO or PRO_PLUS tier.
     * Returns a checkout URL that the client should redirect the user to for
     * payment. The actual subscription entity is created by the webhook handler
     * when payment succeeds (not by this endpoint).
     * </p>
     * <p>
     * Workflow:
     * 1. Create Stripe Checkout session with tier price
     * 2. Return sessionId and checkoutUrl to client
     * 3. Client redirects user to Stripe payment page
     * 4. User completes payment on Stripe
     * 5. Webhook handler creates subscription entity and activates tier
     * </p>
     *
     * @param request CreateCheckoutRequest with tier, successUrl, cancelUrl
     * @return 200 OK with CheckoutSessionResponse (sessionId, checkoutUrl)
     * @throws WebApplicationException 400 Bad Request if validation fails,
     *     401 Unauthorized if not authenticated,
     *     500 Internal Server Error if Stripe API fails
     */
    @POST
    @Path("/subscriptions/checkout")
    @RolesAllowed("USER")
    @Operation(summary = "Create Stripe checkout session for upgrade",
        description = "Creates a Stripe Checkout session for upgrading "
            + "to Pro or Pro Plus tier. Returns checkout URL for "
            + "redirect.")
    @APIResponse(responseCode = "200",
        description = "Checkout session created",
        content = @Content(
            schema = @Schema(
                implementation = CheckoutSessionResponse.class)))
    @APIResponse(responseCode = "400",
        description = "Invalid request parameters",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401",
        description = "Unauthorized",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500",
        description = "Internal server error",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> createCheckoutSession(
            @Valid final CreateCheckoutRequest request) {

        UUID currentUserId = requireCurrentUserId();

        LOG.infof(
            "POST /subscriptions/checkout - user %s, tier %s",
            currentUserId,
            request.tier
        );

        // Wrap blocking StripeAdapter call in Uni
        return Uni.createFrom().item(() -> {
            try {
                CheckoutSessionResult result =
                    stripeAdapter.createCheckoutSession(
                        currentUserId,
                        request.tier,
                        request.successUrl,
                        request.cancelUrl
                    );

                LOG.infof(
                    "Created Stripe checkout session %s for user %s (tier: %s)",
                    result.sessionId(),
                    currentUserId,
                    request.tier
                );

                return result;
            } catch (Exception e) {
                LOG.errorf(
                    e,
                    "Failed to create checkout session for user %s (tier: %s)",
                    currentUserId,
                    request.tier
                );
                throw new RuntimeException(
                    "Failed to create checkout session", e);
            }
        })
        .onItem().transform(result ->
            new CheckoutSessionResponse(
                result.sessionId(),
                result.checkoutUrl()
            )
        )
        .onItem().transform(response -> Response.ok(response).build());
    }

    /**
     * POST /api/v1/subscriptions/{subscriptionId}/cancel - Cancel subscription.
     * <p>
     * Performs a soft cancellation where the subscription remains active until
     * the end of the current billing period. Sets canceledAt timestamp and
     * calls Stripe API to cancel with cancel_at_period_end=true.
     * </p>
     * <p>
     * The user's tier is NOT downgraded immediately - it remains active until
     * currentPeriodEnd. The webhook handler will downgrade to FREE tier when
     * the billing period ends.
     * </p>
     * <p>
     * Authorization: Users can only cancel their own subscriptions. Attempting
     * to cancel another user's subscription returns 403 Forbidden.
     * </p>
     *
     * @param subscriptionIdString Subscription ID path parameter (UUID format)
     * @return 200 OK with updated SubscriptionDTO showing canceledAt timestamp
     * @throws WebApplicationException 400 Bad Request if UUID invalid,
     *     401 Unauthorized if not authenticated,
     *     403 Forbidden if canceling other user's subscription,
     *     404 Not Found if subscription not found,
     *     500 Internal Server Error on failure
     */
    @POST
    @Path("/subscriptions/{subscriptionId}/cancel")
    @RolesAllowed("USER")
    @Operation(summary = "Cancel subscription (end of billing period)",
        description = "Cancels subscription at end of current billing "
            + "period. Access continues until period end.")
    @APIResponse(responseCode = "200",
        description = "Subscription canceled",
        content = @Content(
            schema = @Schema(implementation = SubscriptionDTO.class)))
    @APIResponse(responseCode = "400",
        description = "Invalid UUID format",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401",
        description = "Unauthorized",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403",
        description = "Forbidden - user trying to cancel another "
            + "user's subscription",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404",
        description = "Subscription not found",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500",
        description = "Internal server error",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> cancelSubscription(
            @Parameter(description = "Subscription ID", required = true)
            @PathParam("subscriptionId") final String subscriptionIdString) {

        UUID currentUserId = requireCurrentUserId();

        // Parse and validate subscriptionId
        UUID subscriptionId;
        try {
            subscriptionId = UUID.fromString(subscriptionIdString);
        } catch (IllegalArgumentException e) {
            LOG.warnf(
                "Invalid UUID format for subscriptionId: %s",
                subscriptionIdString
            );
            return Uni.createFrom().item(
                badRequest("Invalid UUID format for subscriptionId"));
        }

        LOG.infof(
            "POST /subscriptions/%s/cancel - requested by user %s",
            subscriptionId,
            currentUserId
        );

        // Fetch subscription and verify ownership
        return subscriptionRepository.findById(subscriptionId)
            .onItem().ifNull().failWith(() ->
                new WebApplicationException(
                    notFoundResponse("Subscription not found"))
            )
            .onItem().transformToUni(subscription -> {
                // Verify user owns this subscription
                if (!subscription.entityId.equals(currentUserId)) {
                    LOG.warnf(
                        "User %s attempted to cancel subscription %s "
                            + "owned by %s",
                        currentUserId,
                        subscriptionId,
                        subscription.entityId
                    );
                    return Uni.createFrom().failure(
                        new WebApplicationException(
                            forbiddenResponse(
                                "User can only cancel own subscription"))
                    );
                }

                // Cancel subscription via BillingService
                return billingService.cancelSubscription(currentUserId)
                    .onItem().transformToUni(voidItem ->
                        // Fetch updated subscription to return
                        // with canceledAt timestamp
                        subscriptionRepository.findById(subscriptionId)
                    );
            })
            .onItem().transform(subscription -> {
                SubscriptionDTO dto = subscriptionMapper.toDTO(subscription);
                LOG.infof(
                    "Canceled subscription %s for user %s",
                    subscriptionId,
                    currentUserId
                );
                return Response.ok(dto).build();
            })
            .onFailure().invoke(throwable ->
                LOG.errorf(
                    throwable,
                    "Failed to cancel subscription %s for user %s",
                    subscriptionId,
                    currentUserId
                )
            );
    }

    /**
     * GET /api/v1/billing/invoices - List payment history.
     * <p>
     * Returns a paginated list of payment invoices for the authenticated user.
     * Invoices are ordered by payment date descending (most recent first).
     * </p>
     * <p>
     * Pagination uses offset-based approach with page/size parameters.
     * Response includes pagination metadata (page, size, totalElements,
     * totalPages).
     * </p>
     * <p>
     * Security: Automatically filtered by current user ID - users only see
     * their own invoices.
     * </p>
     *
     * @param page Page number (0-indexed, default 0)
     * @param size Page size (1-100, default 20)
     * @return 200 OK with InvoiceListResponse containing invoices and
     *         pagination metadata
     * @throws WebApplicationException 400 Bad Request if pagination
     *     params invalid, 401 Unauthorized if not authenticated,
     *     500 Internal Server Error on failure
     */
    @GET
    @Path("/billing/invoices")
    @RolesAllowed("USER")
    @Operation(summary = "List payment history",
        description = "Returns paginated list of payment invoices for "
            + "the authenticated user.")
    @APIResponse(responseCode = "200",
        description = "Invoice list retrieved",
        content = @Content(
            schema = @Schema(
                implementation = InvoiceListResponse.class)))
    @APIResponse(responseCode = "400",
        description = "Invalid pagination parameters",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401",
        description = "Unauthorized",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500",
        description = "Internal server error",
        content = @Content(
            schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> listInvoices(
            @Parameter(description = "Page number (0-indexed)")
            @QueryParam("page") @DefaultValue("0") final int page,
            @Parameter(description = "Page size")
            @QueryParam("size") @DefaultValue("20") final int size) {

        UUID currentUserId = requireCurrentUserId();

        // Validate pagination parameters
        if (page < 0) {
            return Uni.createFrom().item(
                badRequest("Page number must be >= 0"));
        }

        if (size < 1) {
            return Uni.createFrom().item(
                badRequest("Page size must be >= 1"));
        }

        if (size > MAX_PAGE_SIZE) {
            return Uni.createFrom().item(
                badRequest("Page size cannot exceed 100"));
        }

        LOG.infof(
            "GET /billing/invoices - user %s, page %d, size %d",
            currentUserId,
            page,
            size
        );

        // Combine payment query and count query in parallel
        return Uni.combine().all().unis(
            paymentHistoryRepository.findByUserId(currentUserId, page, size),
            paymentHistoryRepository.countByUserId(currentUserId)
        ).asTuple()
            .onItem().transform(tuple -> {
                List<PaymentHistoryDTO> paymentDtos = tuple.getItem1().stream()
                    .map(paymentHistoryMapper::toDTO)
                    .collect(Collectors.toList());

                Long totalElements = tuple.getItem2();

                // Calculate pagination metadata
                int totalPages = totalElements == 0
                    ? 0
                    : (int) Math.ceil((double) totalElements / size);

                LOG.debugf(
                    "Retrieved %d invoices for user %s (page %d/%d, total %d)",
                    paymentDtos.size(),
                    currentUserId,
                    page,
                    totalPages,
                    totalElements
                );

                // Create response with invoices and pagination metadata
                InvoiceListResponse response = new InvoiceListResponse(
                    paymentDtos,
                    page,
                    size,
                    totalElements,
                    totalPages
                );

                return Response.ok(response).build();
            })
        .onFailure().invoke(throwable ->
            LOG.errorf(
                throwable,
                "Failed to retrieve invoices for user %s",
                currentUserId
            )
        );
    }

    /**
     * Gets the current authenticated user ID from security context.
     * Throws 401 Unauthorized if user is not authenticated.
     *
     * @return Current user ID
     * @throws WebApplicationException 401 Unauthorized if not authenticated
     */
    private UUID requireCurrentUserId() {
        UUID userId;
        try {
            userId = securityContext.getCurrentUserId();
        } catch (RuntimeException ex) {
            throw unauthorizedException();
        }

        if (userId == null) {
            throw unauthorizedException();
        }
        return userId;
    }

    /**
     * Creates 400 Bad Request response with error message.
     *
     * @param message Error message
     * @return Response with 400 status
     */
    private Response badRequest(final String message) {
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", message);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(error).build();
    }

    /**
     * Creates 403 Forbidden response with error message.
     *
     * @param message Error message
     * @return Response with 403 status
     */
    private Response forbiddenResponse(final String message) {
        ErrorResponse error = new ErrorResponse("FORBIDDEN", message);
        return Response.status(Response.Status.FORBIDDEN)
            .entity(error).build();
    }

    /**
     * Creates 404 Not Found response with error message.
     *
     * @param message Error message
     * @return Response with 404 status
     */
    private Response notFoundResponse(final String message) {
        ErrorResponse error = new ErrorResponse("NOT_FOUND", message);
        return Response.status(Response.Status.NOT_FOUND)
            .entity(error).build();
    }

    /**
     * Creates 401 Unauthorized exception with standard error message.
     *
     * @return WebApplicationException with 401 status
     */
    private WebApplicationException unauthorizedException() {
        ErrorResponse error = new ErrorResponse(
            "UNAUTHORIZED",
            "User authentication required"
        );
        return new WebApplicationException(
            Response.status(Response.Status.UNAUTHORIZED)
                .entity(error).build()
        );
    }
}
