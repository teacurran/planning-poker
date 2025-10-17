package com.scrumpoker.domain.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for AuditLog entity.
 * Includes timestamp for partition key support.
 */
@Embeddable
public class AuditLogId implements Serializable {

    @Column(name = "log_id")
    public UUID logId;

    @Column(name = "timestamp")
    public Instant timestamp;

    public AuditLogId() {
    }

    public AuditLogId(UUID logId, Instant timestamp) {
        this.logId = logId;
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLogId that = (AuditLogId) o;
        return Objects.equals(logId, that.logId) && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logId, timestamp);
    }
}
