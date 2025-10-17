package com.scrumpoker.domain.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for OrgMember entity.
 * Represents the many-to-many relationship between organizations and users.
 */
@Embeddable
public class OrgMemberId implements Serializable {

    @Column(name = "org_id")
    public UUID orgId;

    @Column(name = "user_id")
    public UUID userId;

    public OrgMemberId() {
    }

    public OrgMemberId(UUID orgId, UUID userId) {
        this.orgId = orgId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrgMemberId that = (OrgMemberId) o;
        return Objects.equals(orgId, that.orgId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId);
    }
}
