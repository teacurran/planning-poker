package com.scrumpoker.domain.organization;

import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * Many-to-many relationship between users and organizations.
 * Tracks organization membership with role assignments.
 */
@Entity
@Table(name = "org_member")
public class OrgMember extends PanacheEntityBase {

    @EmbeddedId
    public OrgMemberId id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("orgId")
    @JoinColumn(name = "org_id", nullable = false, foreignKey = @ForeignKey(name = "fk_org_member_org"))
    public Organization organization;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_org_member_user"))
    public User user;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, columnDefinition = "org_role_enum")
    public OrgRole role = OrgRole.MEMBER;

    @NotNull
    @Column(name = "joined_at", nullable = false, updatable = false)
    public Instant joinedAt = Instant.now();
}
