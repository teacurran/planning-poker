package com.scrumpoker.domain.billing;

import com.scrumpoker.domain.user.SubscriptionTier;
import com.scrumpoker.integration.stripe.StripeAdapter;
import com.scrumpoker.integration.stripe.StripeException;
import com.scrumpoker.repository.SubscriptionRepository;
import com.scrumpoker.repository.UserRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Domain service managing subscription lifecycle for users and organizations.
 * Coordinates between Stripe payment processing and local subscription state.
 * <p>
 * This service handles:
 * - Creating new subscriptions (initiating upgrade to paid tier)
 * - Upgrading existing subscriptions to higher tiers
 * - Canceling subscriptions (soft cancel, active until period end)
 * - Retrieving active subscription for tier enforcement
 * - Syncing subscription status from Stripe webhook events
 * </p>
 * <p>
 * All operations are reactive using Mutiny Uni types and transactional
 * to ensure atomicity of database updates with Stripe API calls.
 * </p>
 */
@ApplicationScoped
public class BillingService {

    /**
     * Logger instance for this class.
     */
    private static final Logger LOG =
        Logger.getLogger(BillingService.class);

    /**
     * Default trial period duration in days for new subscriptions.
     */
    private static final int DEFAULT_TRIAL_PERIOD_DAYS = 30;

    /**
     * Stripe integration adapter for payment processing.
     */
    @Inject
    private StripeAdapter stripeAdapter;

    /**
     * Repository for subscription entity persistence.
     */
    @Inject
    private SubscriptionRepository subscriptionRepository;

    /**
     * Repository for user entity persistence.
     */
    @Inject
    private UserRepository userRepository;

    /**
     * Creates a new subscription for a user upgrading from FREE to paid tier.
     * <p>
     * This method initiates the subscription creation process but does NOT
     * immediately activate the subscription. The subscription is created in
     * TRIALING status with a placeholder Stripe subscription ID. The actual
     * Stripe subscription will be created when the user completes the
     * checkout session (handled by webhook in I5.T3).
     * </p>
     * <p>
     * Workflow:
     * 1. Validate user exists and has no active subscription
     * 2. Validate target tier is not FREE (FREE tier has no subscription)
     * 3. Create Subscription entity with TRIALING status
     * 4. Update User.subscriptionTier to target tier
     * 5. Return subscription entity
     * </p>
     *
     * @param userId The user ID to create subscription for
     * @param tier The target subscription tier (must be PRO, PRO_PLUS, or
     *             ENTERPRISE)
     * @return Uni containing the created Subscription entity
     * @throws IllegalArgumentException if user not found or tier is FREE
     * @throws IllegalStateException if user already has active subscription
     */
    @Transactional
    public Uni<Subscription> createSubscription(final UUID userId,
                                                 final SubscriptionTier tier) {
        LOG.infof("Creating subscription for user %s (tier: %s)",
            userId, tier);

        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("User not found: " + userId))
            .onItem().transformToUni(user -> {
                // Validate tier is not FREE
                if (tier == SubscriptionTier.FREE) {
                    return Uni.createFrom().failure(
                        new IllegalArgumentException(
                            "Cannot create subscription for FREE tier"));
                }

                // Check if user already has an active subscription
                return subscriptionRepository.findActiveByEntityIdAndType(
                        userId, EntityType.USER)
                    .onItem().transformToUni(existingSubscription -> {
                        if (existingSubscription != null) {
                            return Uni.createFrom().failure(
                                new IllegalStateException(
                                    "User already has an active "
                                        + "subscription"));
                        }

                        // Create Subscription entity (checkout session will
                        // be created by controller)
                        Subscription subscription = new Subscription();
                        subscription.stripeSubscriptionId =
                            "pending-checkout-" + UUID.randomUUID();
                        subscription.entityId = userId;
                        subscription.entityType = EntityType.USER;
                        subscription.tier = tier;
                        subscription.status = SubscriptionStatus.TRIALING;
                        subscription.currentPeriodStart = Instant.now();
                        subscription.currentPeriodEnd =
                            Instant.now().plus(DEFAULT_TRIAL_PERIOD_DAYS,
                                ChronoUnit.DAYS);

                        return subscriptionRepository.persist(subscription);
                    });
            })
            .onItem().transformToUni(subscription -> {
                // Update User.subscriptionTier
                return updateUserTier(userId, tier)
                    .replaceWith(subscription);
            })
            .onItem().invoke(subscription ->
                LOG.infof("Created subscription %s for user %s (tier: %s)",
                    subscription.subscriptionId, userId, tier)
            )
            .onFailure().invoke(throwable ->
                LOG.errorf(throwable, "Failed to create subscription for "
                    + "user %s (tier: %s)", userId, tier)
            );
    }

    /**
     * Upgrades an existing paid subscription to a higher tier.
     * <p>
     * This method validates the tier transition is an upgrade (not a
     * downgrade), updates the Stripe subscription with proration, and
     * updates the local subscription entity and user tier.
     * </p>
     * <p>
     * Valid tier upgrades:
     * - FREE → PRO, PRO_PLUS, ENTERPRISE
     * - PRO → PRO_PLUS, ENTERPRISE
     * - PRO_PLUS → ENTERPRISE
     * </p>
     * <p>
     * Downgrades are not allowed via this method. Users must cancel their
     * current subscription and create a new one at the lower tier.
     * </p>
     *
     * @param userId The user ID to upgrade subscription for
     * @param newTier The target subscription tier
     * @return Uni containing the updated Subscription entity
     * @throws IllegalArgumentException if user not found or invalid tier
     *                                  transition
     * @throws IllegalStateException if user has no active subscription
     * @throws StripeException if Stripe API call fails
     */
    @Transactional
    public Uni<Subscription> upgradeSubscription(final UUID userId,
                                                  final SubscriptionTier
                                                      newTier) {
        LOG.infof("Upgrading subscription for user %s to tier %s",
            userId, newTier);

        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("User not found: " + userId))
            .onItem().transformToUni(user -> {
                // Fetch the user's active subscription
                return subscriptionRepository.findActiveByEntityIdAndType(
                        userId, EntityType.USER)
                    .onItem().ifNull().failWith(() ->
                        new IllegalStateException(
                            "User has no active subscription"))
                    .onItem().transformToUni(subscription -> {
                        // Validate tier upgrade
                        if (!isValidUpgrade(subscription.tier, newTier)) {
                            return Uni.createFrom().failure(
                                new IllegalArgumentException(
                                    String.format(
                                        "Invalid tier transition: %s → %s. "
                                            + "Downgrades not allowed via "
                                            + "upgrade method.",
                                        subscription.tier, newTier)));
                        }

                        // Update Stripe subscription (wrap in Uni)
                        return Uni.createFrom().item(() -> {
                            try {
                                stripeAdapter.updateSubscription(
                                    subscription.stripeSubscriptionId,
                                    newTier);
                                return subscription;
                            } catch (StripeException e) {
                                throw new RuntimeException(
                                    "Failed to update Stripe subscription",
                                    e);
                            }
                        });
                    })
                    .onItem().transformToUni(subscription -> {
                        // Update subscription entity tier
                        subscription.tier = newTier;
                        return subscriptionRepository.persist(subscription);
                    })
                    .onItem().transformToUni(subscription -> {
                        // Update User.subscriptionTier
                        return updateUserTier(userId, newTier)
                            .replaceWith(subscription);
                    });
            })
            .onItem().invoke(subscription ->
                LOG.infof("Upgraded subscription %s for user %s to tier %s",
                    subscription.subscriptionId, userId, newTier)
            )
            .onFailure().invoke(throwable ->
                LOG.errorf(throwable, "Failed to upgrade subscription for "
                    + "user %s to tier %s", userId, newTier)
            );
    }

    /**
     * Cancels a user's active subscription (soft cancel).
     * <p>
     * This method performs a graceful cancellation where the subscription
     * remains active until the end of the current billing period. The
     * User.subscriptionTier is NOT updated immediately - it will be
     * downgraded to FREE when the webhook handler receives the
     * subscription.ended event at the end of the billing period.
     * </p>
     * <p>
     * Sets subscription.canceledAt to the current timestamp and calls
     * Stripe API to cancel with cancel_at_period_end=true.
     * </p>
     *
     * @param userId The user ID to cancel subscription for
     * @return Uni<Void> signaling completion
     * @throws IllegalArgumentException if user not found
     * @throws StripeException if Stripe API call fails
     */
    @Transactional
    public Uni<Void> cancelSubscription(final UUID userId) {
        LOG.infof("Canceling subscription for user %s", userId);

        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() ->
                new IllegalArgumentException("User not found: " + userId))
            .onItem().transformToUni(user -> {
                // Fetch the user's active subscription
                return subscriptionRepository.findActiveByEntityIdAndType(
                        userId, EntityType.USER)
                    .onItem().transformToUni(subscription -> {
                        if (subscription == null) {
                            // No active subscription, return successfully
                            // (idempotent)
                            LOG.infof("User %s has no active subscription "
                                + "to cancel", userId);
                            return Uni.createFrom().voidItem();
                        }

                        // Cancel Stripe subscription (wrap in Uni)
                        return Uni.createFrom().item(() -> {
                            try {
                                stripeAdapter.cancelSubscription(
                                    subscription.stripeSubscriptionId);
                                return subscription;
                            } catch (StripeException e) {
                                throw new RuntimeException(
                                    "Failed to cancel Stripe subscription",
                                    e);
                            }
                        })
                        .onItem().transformToUni(sub -> {
                            // Set canceledAt timestamp
                            sub.canceledAt = Instant.now();
                            return subscriptionRepository.persist(sub);
                        })
                        .replaceWithVoid();
                    });
            })
            .onItem().invoke(() ->
                LOG.infof("Canceled subscription for user %s", userId)
            )
            .onFailure().invoke(throwable ->
                LOG.errorf(throwable, "Failed to cancel subscription for "
                    + "user %s", userId)
            );
    }

    /**
     * Retrieves the active subscription for a user.
     * <p>
     * This method is used for tier enforcement to check if a user has
     * access to premium features. Returns null if the user is on the
     * FREE tier (no subscription).
     * </p>
     *
     * @param userId The user ID to retrieve subscription for
     * @return Uni containing the active Subscription entity, or null if no
     *         active subscription exists
     */
    public Uni<Subscription> getActiveSubscription(final UUID userId) {
        LOG.debugf("Fetching active subscription for user %s", userId);

        return subscriptionRepository.findActiveByEntityIdAndType(
                userId, EntityType.USER)
            .onItem().invoke(subscription -> {
                if (subscription != null) {
                    LOG.debugf("Found active subscription %s for user %s "
                        + "(tier: %s)", subscription.subscriptionId,
                        userId, subscription.tier);
                } else {
                    LOG.debugf("No active subscription for user %s", userId);
                }
            });
    }

    /**
     * Syncs subscription status from Stripe webhook events.
     * <p>
     * This method is called by the webhook handler (I5.T3) when Stripe
     * sends subscription lifecycle events (created, updated, canceled,
     * deleted). It updates the local subscription entity to match the
     * Stripe subscription state.
     * </p>
     * <p>
     * Status handling:
     * - ACTIVE: Subscription is active, user has access to tier features
     * - CANCELED: Subscription canceled but still active until period end
     * - PAST_DUE: Payment failed, user retains access during grace period
     * - TRIALING: Subscription in trial period (initial state)
     * </p>
     * <p>
     * User tier updates:
     * - ACTIVE → set User.subscriptionTier to subscription.tier
     * - CANCELED (period ended) → downgrade User.subscriptionTier to FREE
     * - PAST_DUE → keep current tier (grace period)
     * </p>
     * <p>
     * NOTE: This method does NOT start its own transaction. It must be called
     * from within an existing transaction context (either @Transactional or
     * Panache.withTransaction()).
     * </p>
     *
     * @param stripeSubscriptionId The Stripe subscription ID from webhook
     * @param status The new subscription status
     * @return Uni<Void> signaling completion
     */
    public Uni<Void> syncSubscriptionStatus(final String
                                                 stripeSubscriptionId,
                                             final SubscriptionStatus status) {
        LOG.infof("Syncing subscription status for Stripe subscription %s "
            + "to %s", stripeSubscriptionId, status);

        return subscriptionRepository.findByStripeSubscriptionId(
                stripeSubscriptionId)
            .onItem().transformToUni(subscription -> {
                if (subscription == null) {
                    LOG.warnf("Subscription not found for Stripe "
                        + "subscription ID %s", stripeSubscriptionId);
                    return Uni.createFrom().voidItem();
                }

                // Update subscription status
                subscription.status = status;

                // Handle status-specific updates
                if (status == SubscriptionStatus.CANCELED) {
                    // Ensure canceledAt is set
                    if (subscription.canceledAt == null) {
                        subscription.canceledAt = Instant.now();
                    }

                    // Check if current period has ended
                    if (Instant.now().isAfter(
                            subscription.currentPeriodEnd)) {
                        // Subscription has truly ended, downgrade to FREE
                        return subscriptionRepository.persist(subscription)
                            .onItem().transformToUni(sub ->
                                updateUserTier(sub.entityId,
                                    SubscriptionTier.FREE)
                            );
                    }
                }

                if (status == SubscriptionStatus.ACTIVE) {
                    // Update user tier to subscription tier
                    return subscriptionRepository.persist(subscription)
                        .onItem().transformToUni(sub ->
                            updateUserTier(sub.entityId, sub.tier)
                        );
                }

                // For other statuses (PAST_DUE, TRIALING), just persist
                return subscriptionRepository.persist(subscription)
                    .replaceWithVoid();
            })
            .onItem().invoke(() ->
                LOG.infof("Synced subscription status for Stripe "
                    + "subscription %s to %s", stripeSubscriptionId, status)
            )
            .onFailure().invoke(throwable ->
                LOG.errorf(throwable, "Failed to sync subscription status "
                    + "for Stripe subscription %s", stripeSubscriptionId)
            );
    }

    /**
     * Updates the User.subscriptionTier field for tier enforcement.
     * <p>
     * This is a helper method called after subscription changes to ensure
     * the user's tier is always in sync with their active subscription.
     * </p>
     *
     * @param userId The user ID to update
     * @param tier The new subscription tier
     * @return Uni<Void> signaling completion
     */
    private Uni<Void> updateUserTier(final UUID userId,
                                      final SubscriptionTier tier) {
        return userRepository.findById(userId)
            .onItem().ifNull().failWith(() ->
                new IllegalStateException("User not found: " + userId))
            .onItem().transformToUni(user -> {
                user.subscriptionTier = tier;
                return userRepository.persist(user);
            })
            .replaceWithVoid()
            .onItem().invoke(() ->
                LOG.infof("Updated user %s subscription tier to %s",
                    userId, tier)
            );
    }

    /**
     * Validates if a tier transition is a valid upgrade.
     * <p>
     * Valid upgrades are:
     * - FREE → PRO, PRO_PLUS, ENTERPRISE
     * - PRO → PRO_PLUS, ENTERPRISE
     * - PRO_PLUS → ENTERPRISE
     * </p>
     * <p>
     * Downgrades (higher tier to lower tier) return false and must be
     * handled via cancellation flow instead.
     * </p>
     *
     * @param currentTier The current subscription tier
     * @param newTier The target subscription tier
     * @return true if the transition is a valid upgrade, false otherwise
     */
    private boolean isValidUpgrade(final SubscriptionTier currentTier,
                                    final SubscriptionTier newTier) {
        return switch (currentTier) {
            case FREE -> newTier == SubscriptionTier.PRO
                      || newTier == SubscriptionTier.PRO_PLUS
                      || newTier == SubscriptionTier.ENTERPRISE;
            case PRO -> newTier == SubscriptionTier.PRO_PLUS
                     || newTier == SubscriptionTier.ENTERPRISE;
            case PRO_PLUS -> newTier == SubscriptionTier.ENTERPRISE;
            case ENTERPRISE -> false; // Cannot upgrade from highest tier
        };
    }
}
