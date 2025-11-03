package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * POJO for participant summary data stored in SessionHistory JSONB field.
 * Represents a snapshot of participant activity during a session.
 *
 * <p>This class is immutable after construction and thread-safe.
 * Used for serializing participant data to JSONB format in PostgreSQL.
 */
public final class ParticipantSummary {

    /**
     * Unique identifier for the participant.
     */
    @JsonProperty("participant_id")
    private UUID participantId;

    /**
     * Display name shown in the session.
     */
    @JsonProperty("display_name")
    private String displayName;

    /**
     * Participant role (e.g., "owner", "participant", "observer").
     */
    @JsonProperty("role")
    private String role;

    /**
     * Total number of votes cast by this participant.
     */
    @JsonProperty("vote_count")
    private Integer voteCount;

    /**
     * Whether the participant was authenticated (registered user).
     */
    @JsonProperty("is_authenticated")
    private Boolean isAuthenticated;

    /**
     * Default constructor for Jackson deserialization.
     */
    public ParticipantSummary() {
    }

    /**
     * Constructs a new ParticipantSummary with all fields.
     *
     * @param pId The participant ID
     * @param name The display name
     * @param participantRole The participant role
     * @param votes The vote count
     * @param authenticated Whether authenticated
     */
    public ParticipantSummary(final UUID pId, final String name,
                              final String participantRole,
                              final Integer votes,
                              final Boolean authenticated) {
        this.participantId = pId;
        this.displayName = name;
        this.role = participantRole;
        this.voteCount = votes;
        this.isAuthenticated = authenticated;
    }

    /**
     * Gets the participant ID.
     *
     * @return The participant UUID
     */
    public UUID getParticipantId() {
        return participantId;
    }

    /**
     * Sets the participant ID.
     *
     * @param pId The participant ID to set
     */
    public void setParticipantId(final UUID pId) {
        this.participantId = pId;
    }

    /**
     * Gets the display name.
     *
     * @return The display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name.
     *
     * @param name The display name to set
     */
    public void setDisplayName(final String name) {
        this.displayName = name;
    }

    /**
     * Gets the participant role.
     *
     * @return The role string
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the participant role.
     *
     * @param participantRole The role to set
     */
    public void setRole(final String participantRole) {
        this.role = participantRole;
    }

    /**
     * Gets the vote count.
     *
     * @return The total number of votes
     */
    public Integer getVoteCount() {
        return voteCount;
    }

    /**
     * Sets the vote count.
     *
     * @param votes The vote count to set
     */
    public void setVoteCount(final Integer votes) {
        this.voteCount = votes;
    }

    /**
     * Gets the authentication status.
     *
     * @return True if authenticated, false otherwise
     */
    public Boolean getIsAuthenticated() {
        return isAuthenticated;
    }

    /**
     * Sets the authentication status.
     *
     * @param authenticated The authentication status to set
     */
    public void setIsAuthenticated(final Boolean authenticated) {
        this.isAuthenticated = authenticated;
    }
}
