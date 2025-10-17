package com.scrumpoker.domain.user;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * User-specific preferences and default settings.
 * Stores JSONB columns for flexible configuration without schema migrations.
 */
@Entity
@Table(name = "user_preference")
public class UserPreference extends PanacheEntityBase {

    @Id
    @Column(name = "user_id")
    public UUID userId;

    @NotNull
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", foreignKey = @ForeignKey(name = "fk_user_preference_user"))
    public User user;

    @Size(max = 50)
    @Column(name = "default_deck_type", length = 50)
    public String defaultDeckType;

    /**
     * JSONB column: deck_type, timer_enabled, reveal_behavior.
     * Stored as JSON string, serialized/deserialized by application code.
     */
    @Column(name = "default_room_config", columnDefinition = "jsonb")
    public String defaultRoomConfig;

    @Size(max = 20)
    @Column(name = "theme", length = 20)
    public String theme = "light";

    /**
     * JSONB column: email_enabled, push_enabled, notification_types.
     * Stored as JSON string, serialized/deserialized by application code.
     */
    @Column(name = "notification_settings", columnDefinition = "jsonb")
    public String notificationSettings;

    @NotNull
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @NotNull
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
