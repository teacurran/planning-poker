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
import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.integration.stripe.StripeAdapter;
import com.scrumpoker.repository.PaymentHistoryRepository;
import com.scrumpoker.repository.SubscriptionRepository;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for subscription and billing management operations.
 * Provides endpoints for retrieving subscriptions, creating checkout sessions,
 * canceling subscriptions, and viewing payment history.
 * Implements OpenAPI specification from I2.T1.
 */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Subscriptions",
     description = "Subscription and billing management endpoints")
public class SubscriptionController {

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

    /**
     * GET /api/v1/subscriptions/{userId} - Get current subscription.
     * Security: Requires authentication (enforced in Iteration 3).
     *
     * @param userId The user UUID
     * @return Response containing SubscriptionDTO or 404 Not Found
     */
    @GET
    @Path("/subscriptions/{userId}")
    @Operation(summary = "Get current subscription status",
        description = "Returns current subscription tier, billing "
            + "status, and feature limits.")
    @APIResponse(responseCode = "200",
        description = "Subscription retrieved",
        content = @Content(
            schema = @Schema(implementation = SubscriptionDTO.class)))
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
            @PathParam("userId") final UUID userId) {

        // CHECKSTYLE:OFF TodoComment - Auth implementation deferred to I3
        // TODO: Add authentication check (Iteration 3)
        // TODO: Verify user can only access own subscription (403)
        // CHECKSTYLE:ON TodoComment

        return billingService.getActiveSubscription(userId)
            .onItem().transform(subscription -> {
                SubscriptionDTO dto;
                if (subscription == null) {
                    // User is on FREE tier (no subscription record)
                    dto = subscriptionMapper.createFreeTierDTO(userId);
                } else {
                    dto = subscriptionMapper.toDTO(subscription);
                }
                return Response.ok(dto).build();
            });
        // UserNotFoundException is handled by UserNotFoundExceptionMapper
    }

    /**
     * POST /api/v1/subscriptions/checkout - Create checkout session.
     * Security: Requires authentication (enforced in Iteration 3).
     *
     * @param request The checkout request with tier and redirect URLs
     * @return Response containing CheckoutSessionResponse with Stripe URL
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

        // CHECKSTYLE:OFF TodoComment - Auth deferred to I3
        // TODO: Get userId from JWT security context (Iteration 3)
        // CHECKSTYLE:ON TodoComment
        // TEMPORARY: Placeholder until JWT auth implemented
        final UUID userId = UUID.fromString(
            "123e4567-e89b-12d3-a456-426614174000");

        // Step 1: Create subscription (TRIALING, placeholder Stripe ID)
        return billingService.createSubscription(userId, request.tier)
            .onItem().transformToUni(subscription -> {
                // Step 2: Create Stripe checkout session
                return Uni.createFrom().item(() ->
                    stripeAdapter.createCheckoutSession(
                        userId,
                        request.tier,
                        request.successUrl,
                        request.cancelUrl
                    )
                );
            })
            .onItem().transform(checkoutResult -> {
                // Step 3: Map to response DTO and return
                CheckoutSessionResponse response = new CheckoutSessionResponse(
                    checkoutResult.sessionId(),
                    checkoutResult.checkoutUrl()
                );
                return Response.ok(response).build();
            });
        // IllegalArgumentException handled by mapper
        // StripeException handled by StripeExceptionMapper
    }

    /**
     * POST /api/v1/subscriptions/{subscriptionId}/cancel - Cancel.
     * Security: Requires authentication (enforced in Iteration 3).
     *
     * @param subscriptionId The subscription ID
     * @return Response containing updated SubscriptionDTO
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
            @PathParam("subscriptionId") final UUID subscriptionId) {

        // CHECKSTYLE:OFF TodoComment - Auth deferred to I3
        // TODO: Get userId from JWT security context (Iteration 3)
        // TODO: Verify user owns subscription (403 Forbidden)
        // CHECKSTYLE:ON TodoComment

        // Step 1: Look up subscription by ID to get userId
        return subscriptionRepository.findById(subscriptionId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException(
                    "Subscription not found: " + subscriptionId))
            .onItem().transformToUni(subscription -> {
                // Step 2: Verify user subscription (not organization)
                if (subscription.entityType != EntityType.USER) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException(
                            "Cannot cancel organization subscription "
                                + "via this endpoint"));
                }

                // Step 3: Call BillingService.cancelSubscription with userId
                UUID userId = subscription.entityId;
                return billingService.cancelSubscription(userId)
                    .replaceWith(subscriptionId); // Pass subscriptionId to next stage
            })
            .onItem().transformToUni(subId -> {
                // Step 4: Fetch updated subscription and return DTO
                return subscriptionRepository.findById(subId);
            })
            .onItem().transform(subscription -> {
                SubscriptionDTO dto = subscriptionMapper.toDTO(subscription);
                return Response.ok(dto).build();
            });
        // IllegalArgumentException is handled by IllegalArgumentExceptionMapper
        // StripeException is handled by StripeExceptionMapper
    }

    /**
     * GET /api/v1/billing/invoices - List payment history.
     * Security: Requires authentication (enforced in Iteration 3).
     *
     * @param page The page number (0-indexed)
     * @param size The page size
     * @return Response containing InvoiceListResponse with pagination
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

        // CHECKSTYLE:OFF TodoComment - Auth deferred to I3
        // TODO: Get userId from JWT security context (Iteration 3)
        // CHECKSTYLE:ON TodoComment
        // TEMPORARY: Placeholder until JWT auth implemented
        final UUID userId = UUID.fromString(
            "123e4567-e89b-12d3-a456-426614174000");

        // Validate pagination parameters
        int validatedPage = page < 0 ? 0 : page;
        int validatedSize = size < 1 ? 1 : size;
        if (validatedSize > MAX_PAGE_SIZE) {
            validatedSize = MAX_PAGE_SIZE;
        }

        final int finalPage = validatedPage;
        final int finalSize = validatedSize;

        // Fetch payment history and total count in parallel
        Uni<List<PaymentHistoryDTO>> invoicesUni =
            paymentHistoryRepository
                .findByUserId(userId, finalPage, finalSize)
                .onItem().transform(paymentList ->
                    paymentList.stream()
                        .map(paymentHistoryMapper::toDTO)
                        .collect(Collectors.toList())
                );

        Uni<Long> totalCountUni =
            paymentHistoryRepository.countByUserId(userId);

        // Combine results and build response
        return Uni.combine().all().unis(invoicesUni, totalCountUni)
            .asTuple()
            .onItem().transform(tuple -> {
                List<PaymentHistoryDTO> invoices = tuple.getItem1();
                Long totalElements = tuple.getItem2();
                int totalPages = (int) Math.ceil(
                    (double) totalElements / finalSize);

                InvoiceListResponse response = new InvoiceListResponse(
                    invoices,
                    finalPage,
                    finalSize,
                    totalElements,
                    totalPages
                );

                return Response.ok(response).build();
            });
    }
}
