package com.scrumpoker.domain.billing;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stripe subscription record for users or organizations.
 * Represents a billing subscription with polymorphic entity reference.
 */
@Entity
@Table(name = "subscription", uniqueConstraints = {
    @UniqueConstraint(name = "uq_subscription_stripe_id", columnNames = "stripe_subscription_id"),
    @UniqueConstraint(name = "uq_subscription_entity", columnNames = {"entity_id", "entity_type"})
})
@Cacheable
public class Subscription extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "subscription_id")
    public UUID subscriptionId;

    @NotNull
    @Size(max = 100)
    @Column(name = "stripe_subscription_id", nullable = false, length = 100)
    public String stripeSubscriptionId;

    @NotNull
    @Column(name = "entity_id", nullable = false)
    public UUID entityId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, columnDefinition = "entity_type_enum")
    public EntityType entityType;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, columnDefinition = "subscription_tier_enum")
    public com.scrumpoker.domain.user.SubscriptionTier tier = com.scrumpoker.domain.user.SubscriptionTier.FREE;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "subscription_status_enum")
    public SubscriptionStatus status = SubscriptionStatus.TRIALING;

    @NotNull
    @Column(name = "current_period_start", nullable = false)
    public Instant currentPeriodStart;

    @NotNull
    @Column(name = "current_period_end", nullable = false)
    public Instant currentPeriodEnd;

    @Column(name = "canceled_at")
    public Instant canceledAt;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @NotNull
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
