package com.scrumpoker.domain.organization;

import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Immutable audit trail for compliance and security monitoring.
 * Partitioned by timestamp (monthly range partitions) for performance.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends PanacheEntityBase {

    @EmbeddedId
    public AuditLogId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", foreignKey = @ForeignKey(name = "fk_audit_org"))
    public Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_audit_user"))
    public User user;

    @NotNull
    @Size(max = 100)
    @Column(name = "action", nullable = false, length = 100)
    public String action;

    @NotNull
    @Size(max = 50)
    @Column(name = "resource_type", nullable = false, length = 50)
    public String resourceType;

    @Size(max = 100)
    @Column(name = "resource_id", length = 100)
    public String resourceId;

    /**
     * IP address stored as VARCHAR (supports IPv4/IPv6).
     * Note: Changed from PostgreSQL INET type to VARCHAR due to Hibernate Reactive mapping issues.
     */
    @Size(max = 45)  // Sufficient for IPv6 (39 chars) + potential future formats
    @Column(name = "ip_address", length = 45)
    public String ipAddress;

    @Size(max = 500)
    @Column(name = "user_agent", length = 500)
    public String userAgent;

    /**
     * JSONB column: additional context for the audited action.
     * Stored as JSON string, serialized/deserialized by application code.
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    public String metadata;
}
