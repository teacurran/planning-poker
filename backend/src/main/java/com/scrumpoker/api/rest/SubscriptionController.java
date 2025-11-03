package com.scrumpoker.api.rest;

import com.scrumpoker.api.rest.dto.*;
import com.scrumpoker.api.rest.mapper.PaymentHistoryMapper;
import com.scrumpoker.api.rest.mapper.SubscriptionMapper;
import com.scrumpoker.domain.billing.BillingService;
import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.integration.stripe.CheckoutSessionResult;
import com.scrumpoker.integration.stripe.StripeAdapter;
import com.scrumpoker.repository.PaymentHistoryRepository;
import com.scrumpoker.repository.SubscriptionRepository;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
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
@Tag(name = "Subscriptions", description = "Subscription and billing management endpoints")
public class SubscriptionController {

    @Inject
    BillingService billingService;

    @Inject
    StripeAdapter stripeAdapter;

    @Inject
    SubscriptionRepository subscriptionRepository;

    @Inject
    PaymentHistoryRepository paymentHistoryRepository;

    @Inject
    SubscriptionMapper subscriptionMapper;

    @Inject
    PaymentHistoryMapper paymentHistoryMapper;

    /**
     * GET /api/v1/subscriptions/{userId} - Get current subscription status
     * Security: Requires authentication (will be enforced in Iteration 3)
     * Returns: 200 OK with SubscriptionDTO, or 404 Not Found
     */
    @GET
    @Path("/subscriptions/{userId}")
    @Operation(summary = "Get current subscription status",
        description = "Returns current subscription tier, billing status, and feature limits.")
    @APIResponse(responseCode = "200", description = "Subscription retrieved",
        content = @Content(schema = @Schema(implementation = SubscriptionDTO.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - user accessing another user's subscription",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "User not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> getSubscription(
            @Parameter(description = "User UUID", required = true)
            @PathParam("userId") UUID userId) {

        // TODO: Add authentication check when JWT is implemented in Iteration 3
        // TODO: Verify authenticated user can only access their own subscription (403 Forbidden otherwise)

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
     * POST /api/v1/subscriptions/checkout - Create Stripe checkout session for upgrade
     * Security: Requires authentication
     * Creates checkout session and returns Stripe URL for redirect
     * Returns: 200 OK with CheckoutSessionResponse
     */
    @POST
    @Path("/subscriptions/checkout")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "Create Stripe checkout session for upgrade",
        description = "Creates a Stripe Checkout session for upgrading to Pro or Pro Plus tier. Returns checkout URL for redirect.")
    @APIResponse(responseCode = "200", description = "Checkout session created",
        content = @Content(schema = @Schema(implementation = CheckoutSessionResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid request parameters",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> createCheckoutSession(@Valid CreateCheckoutRequest request) {

        // TODO: Get authenticated userId from security context when auth is implemented (Iteration 3)
        // For now, accept userId as a query parameter for testing
        // TEMPORARY: This is insecure and MUST be replaced with JWT authentication
        UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"); // Placeholder

        // Step 1: Create subscription entity in database (TRIALING status with placeholder Stripe ID)
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
        // IllegalArgumentException is handled by IllegalArgumentExceptionMapper
        // StripeException is handled by StripeExceptionMapper (needs to be created if not exists)
    }

    /**
     * POST /api/v1/subscriptions/{subscriptionId}/cancel - Cancel subscription
     * Security: Requires authentication
     * Cancels subscription at end of current billing period
     * Returns: 200 OK with updated SubscriptionDTO
     */
    @POST
    @Path("/subscriptions/{subscriptionId}/cancel")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "Cancel subscription (end of billing period)",
        description = "Cancels subscription at end of current billing period. Access continues until period end.")
    @APIResponse(responseCode = "200", description = "Subscription canceled",
        content = @Content(schema = @Schema(implementation = SubscriptionDTO.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "403", description = "Forbidden - user trying to cancel another user's subscription",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "404", description = "Subscription not found",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> cancelSubscription(
            @Parameter(description = "Subscription ID", required = true)
            @PathParam("subscriptionId") UUID subscriptionId) {

        // TODO: Get authenticated userId from security context when auth is implemented (Iteration 3)
        // TODO: Verify authenticated user owns this subscription (403 Forbidden otherwise)

        // Step 1: Look up subscription by subscriptionId to get the entityId (userId)
        return subscriptionRepository.findById(subscriptionId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("Subscription not found: " + subscriptionId))
            .onItem().transformToUni(subscription -> {
                // Step 2: Verify this is a user subscription (not organization)
                if (subscription.entityType != EntityType.USER) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException("Cannot cancel organization subscription via this endpoint"));
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
     * GET /api/v1/billing/invoices - List payment history
     * Security: Requires authentication
     * Returns paginated list of payment invoices for authenticated user
     * Returns: 200 OK with InvoiceListResponse
     */
    @GET
    @Path("/billing/invoices")
    @RolesAllowed("USER") // Will be enforced when auth is implemented in Iteration 3
    @Operation(summary = "List payment history",
        description = "Returns paginated list of payment invoices for the authenticated user.")
    @APIResponse(responseCode = "200", description = "Invoice list retrieved",
        content = @Content(schema = @Schema(implementation = InvoiceListResponse.class)))
    @APIResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @APIResponse(responseCode = "500", description = "Internal server error",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public Uni<Response> listInvoices(
            @Parameter(description = "Page number (0-indexed)")
            @QueryParam("page") @DefaultValue("0") int page,
            @Parameter(description = "Page size")
            @QueryParam("size") @DefaultValue("20") int size) {

        // TODO: Get authenticated userId from security context when auth is implemented (Iteration 3)
        // TEMPORARY: This is insecure and MUST be replaced with JWT authentication
        UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"); // Placeholder

        // Validate pagination parameters
        if (page < 0) {
            page = 0;
        }
        if (size < 1) {
            size = 1;
        }
        if (size > 100) {
            size = 100;
        }

        int finalPage = page;
        int finalSize = size;

        // Fetch payment history and total count in parallel
        Uni<List<PaymentHistoryDTO>> invoicesUni = paymentHistoryRepository
            .findByUserId(userId, finalPage, finalSize)
            .onItem().transform(paymentList ->
                paymentList.stream()
                    .map(paymentHistoryMapper::toDTO)
                    .collect(Collectors.toList())
            );

        Uni<Long> totalCountUni = paymentHistoryRepository.countByUserId(userId);

        // Combine results and build response
        return Uni.combine().all().unis(invoicesUni, totalCountUni)
            .asTuple()
            .onItem().transform(tuple -> {
                List<PaymentHistoryDTO> invoices = tuple.getItem1();
                Long totalElements = tuple.getItem2();
                int totalPages = (int) Math.ceil((double) totalElements / finalSize);

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
