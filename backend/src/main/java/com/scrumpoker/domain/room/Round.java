package com.scrumpoker.domain.room;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Individual estimation rounds within a room session.
 * Tracks voting progress and calculated statistics.
 */
@Entity
@Table(name = "round", uniqueConstraints = {
    @UniqueConstraint(name = "uq_round_number", columnNames = {"room_id", "round_number"})
})
public class Round extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "round_id")
    public UUID roundId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "fk_round_room"))
    public Room room;

    @NotNull
    @Column(name = "round_number", nullable = false)
    public Integer roundNumber;

    @Size(max = 500)
    @Column(name = "story_title", length = 500)
    public String storyTitle;

    @NotNull
    @Column(name = "started_at", nullable = false, updatable = false)
    public Instant startedAt = Instant.now();

    @Column(name = "revealed_at")
    public Instant revealedAt;

    /**
     * Numeric average of votes (NUMERIC(5,2) in database).
     */
    @Column(name = "average", precision = 5, scale = 2)
    public BigDecimal average;

    /**
     * VARCHAR to support non-numeric cards (?, ∞, ☕).
     */
    @Size(max = 10)
    @Column(name = "median", length = 10)
    public String median;

    @Column(name = "consensus_reached")
    public Boolean consensusReached = false;
}
