package com.scrumpoker.domain.user;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Registered user account with OAuth authentication.
 * Supports soft delete via deleted_at timestamp for audit trail and GDPR compliance.
 */
@Entity
@Table(name = "\"user\"", uniqueConstraints = {
    @UniqueConstraint(name = "uq_user_email", columnNames = "email"),
    @UniqueConstraint(name = "uq_user_oauth", columnNames = {"oauth_provider", "oauth_subject"})
})
@Cacheable
public class User extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "user_id")
    public UUID userId;

    @NotNull
    @Email
    @Size(max = 255)
    @Column(name = "email", nullable = false, length = 255)
    public String email;

    @NotNull
    @Size(max = 50)
    @Column(name = "oauth_provider", nullable = false, length = 50)
    public String oauthProvider;

    @NotNull
    @Size(max = 255)
    @Column(name = "oauth_subject", nullable = false, length = 255)
    public String oauthSubject;

    @NotNull
    @Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    public String displayName;

    @Size(max = 500)
    @Column(name = "avatar_url", length = 500)
    public String avatarUrl;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", nullable = false, columnDefinition = "subscription_tier_enum")
    public SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @NotNull
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    /**
     * Soft delete timestamp for audit trail and GDPR compliance.
     * NULL means the user is active, non-NULL means soft-deleted.
     */
    @Column(name = "deleted_at")
    public Instant deletedAt;
}
