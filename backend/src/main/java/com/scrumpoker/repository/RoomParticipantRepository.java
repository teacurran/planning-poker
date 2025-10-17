package com.scrumpoker.repository;

import com.scrumpoker.domain.room.RoomParticipant;
import com.scrumpoker.domain.room.RoomRole;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

/**
 * Reactive Panache repository for RoomParticipant entity.
 * Manages active session participants with role-based queries.
 */
@ApplicationScoped
public class RoomParticipantRepository implements PanacheRepositoryBase<RoomParticipant, UUID> {

    /**
     * Find all participants in a room, ordered by connection time.
     *
     * @param roomId The room ID (6-character string)
     * @return Uni of list of participants in the room
     */
    public Uni<List<RoomParticipant>> findByRoomId(String roomId) {
        return find("room.roomId = ?1 order by connectedAt", roomId).list();
    }

    /**
     * Find participants in a room with a specific role.
     *
     * @param roomId The room ID
     * @param role The participant role (HOST, VOTER, OBSERVER)
     * @return Uni of list of participants with the specified role
     */
    public Uni<List<RoomParticipant>> findByRoomIdAndRole(String roomId, RoomRole role) {
        return find("room.roomId = ?1 and role = ?2", roomId, role).list();
    }

    /**
     * Find all participants associated with a user across all rooms.
     *
     * @param userId The user ID
     * @return Uni of list of participant records for the user
     */
    public Uni<List<RoomParticipant>> findByUserId(UUID userId) {
        return find("user.userId", userId).list();
    }

    /**
     * Find active voter participants in a room.
     * Voters are participants who can cast votes.
     *
     * @param roomId The room ID
     * @return Uni of list of voter participants
     */
    public Uni<List<RoomParticipant>> findVotersByRoomId(String roomId) {
        return find("room.roomId = ?1 and role = ?2", roomId, RoomRole.VOTER).list();
    }

    /**
     * Count total participants in a room.
     *
     * @param roomId The room ID
     * @return Uni containing the participant count
     */
    public Uni<Long> countByRoomId(String roomId) {
        return count("room.roomId", roomId);
    }

    /**
     * Count voters in a room.
     *
     * @param roomId The room ID
     * @return Uni containing the voter count
     */
    public Uni<Long> countVotersByRoomId(String roomId) {
        return count("room.roomId = ?1 and role = ?2", roomId, RoomRole.VOTER);
    }
}
