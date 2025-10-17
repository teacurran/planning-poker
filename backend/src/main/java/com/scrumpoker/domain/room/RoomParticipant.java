package com.scrumpoker.domain.room;

import com.scrumpoker.domain.user.User;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Active participants in estimation sessions.
 * Supports both authenticated users and anonymous guests.
 */
@Entity
@Table(name = "room_participant", uniqueConstraints = {
    @UniqueConstraint(name = "uq_room_participant_room_user", columnNames = {"room_id", "user_id"}),
    @UniqueConstraint(name = "uq_room_participant_room_anon", columnNames = {"room_id", "anonymous_id"})
})
public class RoomParticipant extends PanacheEntityBase {

    /**
     * Surrogate UUID primary key for participant identity.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "participant_id")
    public UUID participantId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "fk_room_participant_room"))
    public Room room;

    /**
     * NULL for anonymous participants.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_room_participant_user"))
    public User user;

    /**
     * Required when user_id IS NULL for anonymous guests.
     * Check constraint: (user_id IS NOT NULL AND anonymous_id IS NULL) OR (user_id IS NULL AND anonymous_id IS NOT NULL)
     */
    @Size(max = 50)
    @Column(name = "anonymous_id", length = 50)
    public String anonymousId;

    @NotNull
    @Size(max = 100)
    @Column(name = "display_name", nullable = false, length = 100)
    public String displayName;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, columnDefinition = "room_role_enum")
    public RoomRole role = RoomRole.VOTER;

    @NotNull
    @Column(name = "connected_at", nullable = false, updatable = false)
    public Instant connectedAt = Instant.now();

    @Column(name = "disconnected_at")
    public Instant disconnectedAt;
}
