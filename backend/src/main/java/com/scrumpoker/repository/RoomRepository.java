package com.scrumpoker.repository;

import com.scrumpoker.domain.room.PrivacyMode;
import com.scrumpoker.domain.room.Room;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for Room entity.
 * IMPORTANT: Room uses String primary key (6-character nanoid), not UUID.
 */
@ApplicationScoped
public class RoomRepository implements PanacheRepositoryBase<Room, String> {

    /**
     * Find active (non-deleted) rooms by owner ID, ordered by last activity.
     *
     * @param ownerId The owner user ID
     * @return Uni of list of active rooms owned by the user
     */
    public Uni<List<Room>> findActiveByOwnerId(UUID ownerId) {
        return find("owner.userId = ?1 and deletedAt is null order by lastActiveAt desc", ownerId).list();
    }

    /**
     * Find active rooms by organization ID.
     *
     * @param orgId The organization ID
     * @return Uni of list of active rooms in the organization
     */
    public Uni<List<Room>> findByOrgId(UUID orgId) {
        return find("organization.orgId = ?1 and deletedAt is null order by lastActiveAt desc", orgId).list();
    }

    /**
     * Find public rooms ordered by last activity.
     * Used for public room discovery feature.
     *
     * @return Uni of list of public active rooms
     */
    public Uni<List<Room>> findPublicRooms() {
        return find("privacyMode = ?1 and deletedAt is null order by lastActiveAt desc",
                    PrivacyMode.PUBLIC).list();
    }

    /**
     * Find rooms by privacy mode.
     *
     * @param privacyMode The privacy mode (PUBLIC, INVITE_ONLY, ORG_RESTRICTED)
     * @return Uni of list of rooms with the specified privacy mode
     */
    public Uni<List<Room>> findByPrivacyMode(PrivacyMode privacyMode) {
        return find("privacyMode = ?1 and deletedAt is null", privacyMode).list();
    }

    /**
     * Find rooms that have been inactive since a given timestamp.
     * Useful for cleanup or archival processes.
     *
     * @param inactiveSince The timestamp threshold
     * @return Uni of list of inactive rooms
     */
    public Uni<List<Room>> findInactiveSince(Instant inactiveSince) {
        return find("lastActiveAt < ?1 and deletedAt is null", inactiveSince).list();
    }

    /**
     * Count active rooms by owner.
     *
     * @param ownerId The owner user ID
     * @return Uni containing the count of active rooms
     */
    public Uni<Long> countActiveByOwnerId(UUID ownerId) {
        return count("owner.userId = ?1 and deletedAt is null", ownerId);
    }

    /**
     * Count active rooms in an organization.
     *
     * @param orgId The organization ID
     * @return Uni containing the count of active rooms
     */
    public Uni<Long> countByOrgId(UUID orgId) {
        return count("organization.orgId = ?1 and deletedAt is null", orgId);
    }
}
