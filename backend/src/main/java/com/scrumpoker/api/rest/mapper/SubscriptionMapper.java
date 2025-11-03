package com.scrumpoker.api.rest.mapper;

import com.scrumpoker.api.rest.dto.SubscriptionDTO;
import com.scrumpoker.domain.billing.EntityType;
import com.scrumpoker.domain.billing.Subscription;
import com.scrumpoker.domain.billing.SubscriptionStatus;
import com.scrumpoker.domain.user.SubscriptionTier;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapper for converting between Subscription entity and SubscriptionDTO.
 * Follows the same pattern as UserMapper for consistency.
 */
@ApplicationScoped
public class SubscriptionMapper {

    /**
     * Converts a Subscription entity to SubscriptionDTO for REST API responses.
     *
     * @param subscription The subscription entity
     * @return SubscriptionDTO with all fields mapped
     */
    public SubscriptionDTO toDTO(Subscription subscription) {
        if (subscription == null) {
            return null;
        }

        SubscriptionDTO dto = new SubscriptionDTO();
        dto.subscriptionId = subscription.subscriptionId;
        dto.stripeSubscriptionId = subscription.stripeSubscriptionId;
        dto.entityId = subscription.entityId;
        dto.entityType = subscription.entityType;
        dto.tier = subscription.tier;
        dto.status = subscription.status;
        dto.currentPeriodStart = subscription.currentPeriodStart;
        dto.currentPeriodEnd = subscription.currentPeriodEnd;
        dto.canceledAt = subscription.canceledAt;
        dto.createdAt = subscription.createdAt;
        // Note: updatedAt is intentionally excluded (not in OpenAPI spec)

        return dto;
    }

    /**
     * Creates a default SubscriptionDTO for FREE tier users (no subscription).
     * Used when a user has no active subscription record.
     *
     * @param userId The user ID
     * @return SubscriptionDTO with FREE tier defaults
     */
    public SubscriptionDTO createFreeTierDTO(UUID userId) {
        SubscriptionDTO dto = new SubscriptionDTO();
        dto.subscriptionId = null;
        dto.stripeSubscriptionId = null;
        dto.entityId = userId;
        dto.entityType = EntityType.USER;
        dto.tier = SubscriptionTier.FREE;
        dto.status = SubscriptionStatus.ACTIVE;
        dto.currentPeriodStart = Instant.now();
        dto.currentPeriodEnd = Instant.now().plus(365, java.time.temporal.ChronoUnit.DAYS);
        dto.canceledAt = null;
        dto.createdAt = Instant.now();

        return dto;
    }
}
