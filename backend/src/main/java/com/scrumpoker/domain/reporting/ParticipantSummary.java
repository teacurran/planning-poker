package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * POJO for participant summary data stored in SessionHistory JSONB field.
 * Represents a snapshot of participant activity during a session.
 */
public class ParticipantSummary {

    @JsonProperty("participant_id")
    private UUID participantId;

    @JsonProperty("display_name")
    private String displayName;

    @JsonProperty("role")
    private String role;

    @JsonProperty("vote_count")
    private Integer voteCount;

    @JsonProperty("is_authenticated")
    private Boolean isAuthenticated;

    public ParticipantSummary() {
    }

    public ParticipantSummary(UUID participantId, String displayName, String role, Integer voteCount, Boolean isAuthenticated) {
        this.participantId = participantId;
        this.displayName = displayName;
        this.role = role;
        this.voteCount = voteCount;
        this.isAuthenticated = isAuthenticated;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public void setParticipantId(UUID participantId) {
        this.participantId = participantId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(Integer voteCount) {
        this.voteCount = voteCount;
    }

    public Boolean getIsAuthenticated() {
        return isAuthenticated;
    }

    public void setIsAuthenticated(Boolean isAuthenticated) {
        this.isAuthenticated = isAuthenticated;
    }
}
