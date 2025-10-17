package com.scrumpoker.domain.organization;

import com.scrumpoker.domain.billing.Subscription;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Enterprise SSO workspace with custom branding.
 * Supports OIDC/SAML2 configuration via JSONB for flexibility.
 */
@Entity
@Table(name = "organization", uniqueConstraints = {
    @UniqueConstraint(name = "uq_organization_domain", columnNames = "domain")
})
@Cacheable
public class Organization extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "org_id")
    public UUID orgId;

    @NotNull
    @Size(max = 255)
    @Column(name = "name", nullable = false, length = 255)
    public String name;

    @NotNull
    @Size(max = 255)
    @Column(name = "domain", nullable = false, length = 255)
    public String domain;

    /**
     * JSONB column: OIDC/SAML2 configuration.
     * Stored as JSON string, serialized/deserialized by application code.
     */
    @Column(name = "sso_config", columnDefinition = "jsonb")
    public String ssoConfig;

    /**
     * JSONB column: logo_url, primary_color, secondary_color.
     * Stored as JSON string, serialized/deserialized by application code.
     */
    @Column(name = "branding", columnDefinition = "jsonb")
    public String branding;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id", foreignKey = @ForeignKey(name = "fk_organization_subscription"))
    public Subscription subscription;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @NotNull
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
