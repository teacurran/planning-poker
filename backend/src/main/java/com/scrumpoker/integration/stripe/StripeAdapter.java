package com.scrumpoker.integration.stripe;

import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;
import com.stripe.Stripe;
import com.stripe.model.Customer;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionUpdateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stripe integration adapter wrapping the Stripe Java SDK.
 * Provides methods for subscription management: checkout session creation,
 * customer creation, subscription retrieval, cancellation, and updates.
 * <p>
 * All methods are synchronous (blocking) as the Stripe SDK is blocking.
 * The service layer will handle wrapping these calls in reactive Uni types.
 * </p>
 */
@ApplicationScoped
public class StripeAdapter {

    private static final Logger LOG = Logger.getLogger(StripeAdapter.class);

    @ConfigProperty(name = "stripe.api-key")
    String stripeApiKey;

    @ConfigProperty(name = "stripe.price.pro")
    String proPriceId;

    @ConfigProperty(name = "stripe.price.pro-plus")
    String proPlusPriceId;

    @ConfigProperty(name = "stripe.price.enterprise")
    String enterprisePriceId;

    /**
     * Initializes the Stripe SDK with the API key.
     * Called once when the bean is created.
     */
    @PostConstruct
    void init() {
        Stripe.apiKey = stripeApiKey;
        LOG.infof("Stripe SDK initialized with API key (test mode: %s)",
                  stripeApiKey.startsWith("sk_test_"));
    }

    /**
     * Creates a Stripe checkout session for subscription upgrade.
     *
     * @param userId The user's unique identifier (stored in customer metadata)
     * @param tier The target subscription tier (PRO, PRO_PLUS, or ENTERPRISE)
     * @param successUrl URL to redirect on successful payment
     * @param cancelUrl URL to redirect on canceled payment
     * @return CheckoutSessionResult containing sessionId and checkoutUrl
     * @throws StripeException if Stripe API call fails
     */
    public CheckoutSessionResult createCheckoutSession(UUID userId, SubscriptionTier tier,
                                                       String successUrl, String cancelUrl) {
        try {
            String priceId = getPriceIdForTier(tier);

            if (priceId == null) {
                throw new StripeException("Cannot create checkout session for tier: " + tier +
                                        " (FREE tier has no price)");
            }

            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setPrice(priceId)
                        .setQuantity(1L)
                        .build()
                )
                .putMetadata("userId", userId.toString())
                .putMetadata("tier", tier.name());

            Session session = Session.create(paramsBuilder.build());

            LOG.infof("Created Stripe checkout session %s for user %s (tier: %s)",
                     session.getId(), userId, tier);

            return new CheckoutSessionResult(session.getId(), session.getUrl());

        } catch (com.stripe.exception.StripeException e) {
            LOG.errorf(e, "Failed to create checkout session for user %s (tier: %s)", userId, tier);
            throw new StripeException("Failed to create checkout session", e);
        }
    }

    /**
     * Creates a Stripe customer for the user.
     *
     * @param userId The user's unique identifier (stored in metadata)
     * @param email The user's email address (customer email)
     * @return The Stripe customer ID (cus_...)
     * @throws StripeException if customer creation fails
     */
    public String createCustomer(UUID userId, String email) {
        try {
            CustomerCreateParams params = CustomerCreateParams.builder()
                .setEmail(email)
                .putMetadata("userId", userId.toString())
                .setDescription("Planning Poker user: " + email)
                .build();

            Customer customer = Customer.create(params);

            LOG.infof("Created Stripe customer %s for user %s (%s)",
                     customer.getId(), userId, email);

            return customer.getId();

        } catch (com.stripe.exception.StripeException e) {
            LOG.errorf(e, "Failed to create Stripe customer for user %s (%s)", userId, email);
            throw new StripeException("Failed to create customer", e);
        }
    }

    /**
     * Retrieves a Stripe subscription by ID.
     *
     * @param stripeSubscriptionId The Stripe subscription ID (sub_...)
     * @return StripeSubscriptionInfo with subscription details
     * @throws StripeException if retrieval fails
     */
    public StripeSubscriptionInfo getSubscription(String stripeSubscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);

            return mapToSubscriptionInfo(subscription);

        } catch (com.stripe.exception.StripeException e) {
            LOG.errorf(e, "Failed to retrieve Stripe subscription %s", stripeSubscriptionId);
            throw new StripeException("Failed to retrieve subscription", e);
        }
    }

    /**
     * Cancels a Stripe subscription at period end.
     * The subscription remains active until the end of the current billing period.
     *
     * @param stripeSubscriptionId The Stripe subscription ID to cancel
     * @throws StripeException if cancellation fails
     */
    public void cancelSubscription(String stripeSubscriptionId) {
        try {
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);

            // Cancel at period end (subscription remains active until end of billing period)
            Map<String, Object> params = new HashMap<>();
            params.put("cancel_at_period_end", true);

            subscription.update(params);

            LOG.infof("Canceled Stripe subscription %s (cancel_at_period_end=true)",
                     stripeSubscriptionId);

        } catch (com.stripe.exception.StripeException e) {
            LOG.errorf(e, "Failed to cancel Stripe subscription %s", stripeSubscriptionId);
            throw new StripeException("Failed to cancel subscription", e);
        }
    }

    /**
     * Updates a Stripe subscription to a new tier/price.
     * Performs an immediate proration and changes the subscription price.
     *
     * @param stripeSubscriptionId The Stripe subscription ID to update
     * @param newTier The new subscription tier
     * @throws StripeException if update fails
     */
    public void updateSubscription(String stripeSubscriptionId, SubscriptionTier newTier) {
        try {
            String newPriceId = getPriceIdForTier(newTier);

            if (newPriceId == null) {
                throw new StripeException("Cannot update to tier: " + newTier +
                                        " (FREE tier has no price - use cancelSubscription instead)");
            }

            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);

            // Get the first subscription item (we only have one price per subscription)
            SubscriptionItem item = subscription.getItems().getData().get(0);

            SubscriptionUpdateParams params = SubscriptionUpdateParams.builder()
                .addItem(
                    SubscriptionUpdateParams.Item.builder()
                        .setId(item.getId())
                        .setPrice(newPriceId)
                        .build()
                )
                .setProrationBehavior(SubscriptionUpdateParams.ProrationBehavior.CREATE_PRORATIONS)
                .putMetadata("tier", newTier.name())
                .build();

            subscription.update(params);

            LOG.infof("Updated Stripe subscription %s to tier %s (price: %s)",
                     stripeSubscriptionId, newTier, newPriceId);

        } catch (com.stripe.exception.StripeException e) {
            LOG.errorf(e, "Failed to update Stripe subscription %s to tier %s",
                      stripeSubscriptionId, newTier);
            throw new StripeException("Failed to update subscription", e);
        }
    }

    /**
     * Maps Stripe subscription object to domain DTO.
     *
     * @param subscription Stripe subscription object
     * @return StripeSubscriptionInfo DTO
     */
    private StripeSubscriptionInfo mapToSubscriptionInfo(Subscription subscription) {
        // Derive tier from price ID
        String priceId = subscription.getItems().getData().get(0).getPrice().getId();
        SubscriptionTier tier = getTierFromPriceId(priceId);

        // Map Stripe status to domain status
        SubscriptionStatus status = mapStatus(subscription.getStatus());

        // Convert timestamps
        Instant currentPeriodStart = Instant.ofEpochSecond(subscription.getCurrentPeriodStart());
        Instant currentPeriodEnd = Instant.ofEpochSecond(subscription.getCurrentPeriodEnd());
        Instant canceledAt = subscription.getCanceledAt() != null
            ? Instant.ofEpochSecond(subscription.getCanceledAt())
            : null;

        return new StripeSubscriptionInfo(
            subscription.getId(),
            subscription.getCustomer(),
            tier,
            status,
            currentPeriodStart,
            currentPeriodEnd,
            canceledAt
        );
    }

    /**
     * Maps Stripe status string to domain SubscriptionStatus enum.
     *
     * @param stripeStatus Stripe subscription status
     * @return Domain SubscriptionStatus
     */
    private SubscriptionStatus mapStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.ACTIVE;
            case "past_due" -> SubscriptionStatus.PAST_DUE;
            case "canceled" -> SubscriptionStatus.CANCELED;
            case "trialing" -> SubscriptionStatus.TRIALING;
            case "incomplete", "incomplete_expired", "unpaid" -> SubscriptionStatus.PAST_DUE;
            default -> {
                LOG.warnf("Unknown Stripe status '%s', defaulting to PAST_DUE", stripeStatus);
                yield SubscriptionStatus.PAST_DUE;
            }
        };
    }

    /**
     * Gets the Stripe price ID for a subscription tier.
     *
     * @param tier The subscription tier
     * @return Stripe price ID, or null for FREE tier
     */
    private String getPriceIdForTier(SubscriptionTier tier) {
        return switch (tier) {
            case FREE -> null;
            case PRO -> proPriceId;
            case PRO_PLUS -> proPlusPriceId;
            case ENTERPRISE -> enterprisePriceId;
        };
    }

    /**
     * Derives the subscription tier from Stripe price ID.
     *
     * @param priceId Stripe price ID
     * @return Subscription tier
     */
    private SubscriptionTier getTierFromPriceId(String priceId) {
        if (priceId.equals(proPriceId)) {
            return SubscriptionTier.PRO;
        } else if (priceId.equals(proPlusPriceId)) {
            return SubscriptionTier.PRO_PLUS;
        } else if (priceId.equals(enterprisePriceId)) {
            return SubscriptionTier.ENTERPRISE;
        } else {
            LOG.warnf("Unknown price ID '%s', defaulting to FREE tier", priceId);
            return SubscriptionTier.FREE;
        }
    }
}
