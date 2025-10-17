package com.scrumpoker.repository;

import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.domain.billing.Subscription;
import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for Subscription entity.
 * Handles polymorphic entity references (User or Organization subscriptions).
 */
@ApplicationScoped
public class SubscriptionRepository implements PanacheRepositoryBase<Subscription, UUID> {

    /**
     * Find active subscription by entity ID and type.
     * This is the primary query for subscription tier enforcement.
     *
     * @param entityId The entity ID (user or organization)
     * @param entityType The entity type (USER or ORG)
     * @return Uni containing the active subscription if found, or null if not found
     */
    public Uni<Subscription> findActiveByEntityIdAndType(UUID entityId, EntityType entityType) {
        return find("entityId = ?1 and entityType = ?2 and status = ?3",
                    entityId, entityType, SubscriptionStatus.ACTIVE).firstResult();
    }

    /**
     * Find subscription by entity ID and type (any status).
     *
     * @param entityId The entity ID (user or organization)
     * @param entityType The entity type (USER or ORG)
     * @return Uni containing the subscription if found, or null if not found
     */
    public Uni<Subscription> findByEntityIdAndType(UUID entityId, EntityType entityType) {
        return find("entityId = ?1 and entityType = ?2", entityId, entityType).firstResult();
    }

    /**
     * Find subscription by Stripe subscription ID.
     * Used for webhook processing and payment updates.
     *
     * @param stripeSubscriptionId The Stripe subscription ID
     * @return Uni containing the subscription if found, or null if not found
     */
    public Uni<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId) {
        return find("stripeSubscriptionId", stripeSubscriptionId).firstResult();
    }

    /**
     * Find all subscriptions with a specific status.
     *
     * @param status The subscription status
     * @return Uni of list of subscriptions with the specified status
     */
    public Uni<List<Subscription>> findByStatus(SubscriptionStatus status) {
        return find("status", status).list();
    }

    /**
     * Find all subscriptions for a specific tier.
     *
     * @param tier The subscription tier
     * @return Uni of list of subscriptions with the specified tier
     */
    public Uni<List<Subscription>> findByTier(SubscriptionTier tier) {
        return find("tier", tier).list();
    }

    /**
     * Find all user subscriptions (excluding organization subscriptions).
     *
     * @return Uni of list of user subscriptions
     */
    public Uni<List<Subscription>> findUserSubscriptions() {
        return find("entityType", EntityType.USER).list();
    }

    /**
     * Find all organization subscriptions.
     *
     * @return Uni of list of organization subscriptions
     */
    public Uni<List<Subscription>> findOrgSubscriptions() {
        return find("entityType", EntityType.ORG).list();
    }

    /**
     * Count active subscriptions by tier.
     *
     * @param tier The subscription tier
     * @return Uni containing the count of active subscriptions
     */
    public Uni<Long> countActiveByTier(SubscriptionTier tier) {
        return count("tier = ?1 and status = ?2", tier, SubscriptionStatus.ACTIVE);
    }
}
