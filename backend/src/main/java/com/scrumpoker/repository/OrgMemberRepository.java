package com.scrumpoker.repository;

import com.scrumpoker.domain.organization.OrgMember;
import com.scrumpoker.domain.organization.OrgMemberId;
import com.scrumpoker.domain.organization.OrgRole;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for OrgMember entity.
 * Uses composite primary key (orgId + userId) for the many-to-many relationship.
 */
@ApplicationScoped
public class OrgMemberRepository implements PanacheRepositoryBase<OrgMember, OrgMemberId> {

    /**
     * Find all members of an organization.
     *
     * @param orgId The organization ID
     * @return Uni of list of organization members
     */
    public Uni<List<OrgMember>> findByOrgId(UUID orgId) {
        return find("id.orgId", orgId).list();
    }

    /**
     * Find all organizations for a user.
     *
     * @param userId The user ID
     * @return Uni of list of organization memberships for the user
     */
    public Uni<List<OrgMember>> findByUserId(UUID userId) {
        return find("id.userId", userId).list();
    }

    /**
     * Find members of an organization with a specific role.
     *
     * @param orgId The organization ID
     * @param role The organization role (ADMIN or MEMBER)
     * @return Uni of list of members with the specified role
     */
    public Uni<List<OrgMember>> findByOrgIdAndRole(UUID orgId, OrgRole role) {
        return find("id.orgId = ?1 and role = ?2", orgId, role).list();
    }

    /**
     * Find organization membership by composite key.
     *
     * @param orgId The organization ID
     * @param userId The user ID
     * @return Uni containing the membership if found, or null if not found
     */
    public Uni<OrgMember> findByOrgIdAndUserId(UUID orgId, UUID userId) {
        return findById(new OrgMemberId(orgId, userId));
    }

    /**
     * Check if a user is an admin of an organization.
     *
     * @param orgId The organization ID
     * @param userId The user ID
     * @return Uni containing true if the user is an admin, false otherwise
     */
    public Uni<Boolean> isAdmin(UUID orgId, UUID userId) {
        return find("id.orgId = ?1 and id.userId = ?2 and role = ?3",
                    orgId, userId, OrgRole.ADMIN)
            .count()
            .map(count -> count > 0);
    }

    /**
     * Count total members in an organization.
     *
     * @param orgId The organization ID
     * @return Uni containing the member count
     */
    public Uni<Long> countByOrgId(UUID orgId) {
        return count("id.orgId", orgId);
    }
}
