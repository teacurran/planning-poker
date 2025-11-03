package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * DTO for basic session summary (Free tier).
 * Contains high-level session metrics without detailed round-by-round data.
 *
 * <p>This DTO is returned for all tier levels but represents the maximum
 * detail available to Free tier users. Pro and higher tiers can access
 * additional detailed reports via {@link DetailedSessionReportDTO}.
 */
public class SessionSummaryDTO {

    /**
     * Unique session identifier.
     */
    @JsonProperty("session_id")
    private UUID sessionId;

    /**
     * Room title at the time of the session.
     */
    @JsonProperty("room_title")
    private String roomTitle;

    /**
     * Session start timestamp (UTC).
     */
    @JsonProperty("started_at")
    private Instant startedAt;

    /**
     * Session end timestamp (UTC).
     */
    @JsonProperty("ended_at")
    private Instant endedAt;

    /**
     * Total number of unique stories estimated.
     */
    @JsonProperty("total_stories")
    private Integer totalStories;

    /**
     * Total number of estimation rounds (including re-votes).
     */
    @JsonProperty("total_rounds")
    private Integer totalRounds;

    /**
     * Percentage of rounds that reached consensus (0.0 to 1.0).
     */
    @JsonProperty("consensus_rate")
    private BigDecimal consensusRate;

    /**
     * Average vote value across all rounds (numeric votes only).
     * Excludes non-numeric cards (?, ∞, ☕).
     */
    @JsonProperty("average_vote")
    private BigDecimal averageVote;

    /**
     * Number of participants in the session.
     */
    @JsonProperty("participant_count")
    private Integer participantCount;

    /**
     * Total number of votes cast across all rounds.
     */
    @JsonProperty("total_votes")
    private Integer totalVotes;

    /**
     * Default constructor for Jackson deserialization.
     */
    public SessionSummaryDTO() {
    }

    /**
     * Constructs a SessionSummaryDTO with all fields.
     *
     * @param sessionId Session UUID
     * @param roomTitle Room title
     * @param startedAt Session start time
     * @param endedAt Session end time
     * @param totalStories Story count
     * @param totalRounds Round count
     * @param consensusRate Consensus rate
     * @param averageVote Average vote value
     * @param participantCount Participant count
     * @param totalVotes Total vote count
     */
    public SessionSummaryDTO(final UUID sessionId,
                             final String roomTitle,
                             final Instant startedAt,
                             final Instant endedAt,
                             final Integer totalStories,
                             final Integer totalRounds,
                             final BigDecimal consensusRate,
                             final BigDecimal averageVote,
                             final Integer participantCount,
                             final Integer totalVotes) {
        this.sessionId = sessionId;
        this.roomTitle = roomTitle;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.totalStories = totalStories;
        this.totalRounds = totalRounds;
        this.consensusRate = consensusRate;
        this.averageVote = averageVote;
        this.participantCount = participantCount;
        this.totalVotes = totalVotes;
    }

    // Getters and setters

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(final UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getRoomTitle() {
        return roomTitle;
    }

    public void setRoomTitle(final String roomTitle) {
        this.roomTitle = roomTitle;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(final Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(final Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getTotalStories() {
        return totalStories;
    }

    public void setTotalStories(final Integer totalStories) {
        this.totalStories = totalStories;
    }

    public Integer getTotalRounds() {
        return totalRounds;
    }

    public void setTotalRounds(final Integer totalRounds) {
        this.totalRounds = totalRounds;
    }

    public BigDecimal getConsensusRate() {
        return consensusRate;
    }

    public void setConsensusRate(final BigDecimal consensusRate) {
        this.consensusRate = consensusRate;
    }

    public BigDecimal getAverageVote() {
        return averageVote;
    }

    public void setAverageVote(final BigDecimal averageVote) {
        this.averageVote = averageVote;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

    public void setParticipantCount(final Integer participantCount) {
        this.participantCount = participantCount;
    }

    public Integer getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(final Integer totalVotes) {
        this.totalVotes = totalVotes;
    }
}
