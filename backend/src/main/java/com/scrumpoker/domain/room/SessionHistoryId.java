package com.scrumpoker.domain.room;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for SessionHistory entity.
 * Includes started_at for partition key support.
 */
@Embeddable
public class SessionHistoryId implements Serializable {

    @Column(name = "session_id")
    public UUID sessionId;

    @Column(name = "started_at")
    public Instant startedAt;

    public SessionHistoryId() {
    }

    public SessionHistoryId(UUID sessionId, Instant startedAt) {
        this.sessionId = sessionId;
        this.startedAt = startedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionHistoryId that = (SessionHistoryId) o;
        return Objects.equals(sessionId, that.sessionId) && Objects.equals(startedAt, that.startedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, startedAt);
    }
}
