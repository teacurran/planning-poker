package com.scrumpoker.domain.room;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

/**
 * Individual votes cast by participants in estimation rounds.
 * Immutable once created to maintain audit trail.
 */
@Entity
@Table(name = "vote", uniqueConstraints = {
    @UniqueConstraint(name = "uq_vote_participant", columnNames = {"round_id", "participant_id"})
})
public class Vote extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "vote_id")
    public UUID voteId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vote_round"))
    public Round round;

    /**
     * Foreign key to room_participant.participant_id (UUID).
     */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false, foreignKey = @ForeignKey(name = "fk_vote_participant"))
    public RoomParticipant participant;

    /**
     * Deck card value (0, 1, 2, 3, 5, 8, 13, ?, ∞, ☕).
     */
    @NotNull
    @Size(max = 10)
    @Column(name = "card_value", nullable = false, length = 10)
    public String cardValue;

    @NotNull
    @Column(name = "voted_at", nullable = false, updatable = false)
    public Instant votedAt = Instant.now();
}
