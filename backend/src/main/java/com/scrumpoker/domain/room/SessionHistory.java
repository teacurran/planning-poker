package com.scrumpoker.domain.room;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Completed session records partitioned by month for performance.
 * Stores immutable session snapshots with participant data and statistics.
 */
@Entity
@Table(name = "session_history")
public class SessionHistory extends PanacheEntityBase {

    /**
     * Composite primary key containing session ID and started_at timestamp.
     * Required for PostgreSQL monthly range partitioning.
     */
    @EmbeddedId
    public SessionHistoryId id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false, foreignKey = @ForeignKey(name = "fk_session_room"))
    public Room room;

    @NotNull
    @Column(name = "ended_at", nullable = false)
    public Instant endedAt;

    @NotNull
    @Column(name = "total_rounds", nullable = false)
    public Integer totalRounds;

    @NotNull
    @Column(name = "total_stories", nullable = false)
    public Integer totalStories;

    /**
     * JSONB array of participant snapshots.
     * Stored as JSON string, serialized/deserialized by application code.
     */
    @NotNull
    @Column(name = "participants", nullable = false, columnDefinition = "jsonb")
    public String participants;

    /**
     * JSONB: avg_estimation_time, consensus_rate, total_votes.
     * Stored as JSON string, serialized/deserialized by application code.
     */
    @NotNull
    @Column(name = "summary_stats", nullable = false, columnDefinition = "jsonb")
    public String summaryStats;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
