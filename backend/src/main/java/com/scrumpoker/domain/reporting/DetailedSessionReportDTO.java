package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for detailed session report (Pro tier and above).
 * Extends basic summary with round-by-round breakdown and user consistency metrics.
 *
 * <p>This report is only accessible to users with PRO, PRO_PLUS, or ENTERPRISE
 * subscription tiers. Free tier users attempting to access this report will
 * receive a 403 Forbidden response via {@link FeatureGate} enforcement.
 *
 * <p>Includes all fields from {@link SessionSummaryDTO} plus:
 * <ul>
 *   <li>Round-by-round voting details with individual votes</li>
 *   <li>User consistency metrics (vote variance across rounds)</li>
 * </ul>
 */
public class DetailedSessionReportDTO {

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
     * Round-by-round breakdown with individual votes.
     * Ordered by round number ascending.
     */
    @JsonProperty("rounds")
    private List<RoundDetailDTO> rounds;

    /**
     * User consistency metrics (standard deviation of votes per participant).
     * Map key: participant display name
     * Map value: standard deviation of numeric votes (lower = more consistent)
     *
     * <p>Non-numeric votes (?, ∞, ☕) are excluded from calculations.
     * Participants with fewer than 2 numeric votes will have null values.
     */
    @JsonProperty("user_consistency")
    private Map<String, BigDecimal> userConsistency;

    /**
     * Default constructor for Jackson deserialization.
     */
    public DetailedSessionReportDTO() {
    }

    /**
     * Constructs a DetailedSessionReportDTO with all fields.
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
     * @param rounds Round details list
     * @param userConsistency User consistency map
     */
    public DetailedSessionReportDTO(final UUID sessionId,
                                    final String roomTitle,
                                    final Instant startedAt,
                                    final Instant endedAt,
                                    final Integer totalStories,
                                    final Integer totalRounds,
                                    final BigDecimal consensusRate,
                                    final BigDecimal averageVote,
                                    final Integer participantCount,
                                    final Integer totalVotes,
                                    final List<RoundDetailDTO> rounds,
                                    final Map<String, BigDecimal> userConsistency) {
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
        this.rounds = rounds;
        this.userConsistency = userConsistency;
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

    public List<RoundDetailDTO> getRounds() {
        return rounds;
    }

    public void setRounds(final List<RoundDetailDTO> rounds) {
        this.rounds = rounds;
    }

    public Map<String, BigDecimal> getUserConsistency() {
        return userConsistency;
    }

    public void setUserConsistency(final Map<String, BigDecimal> userConsistency) {
        this.userConsistency = userConsistency;
    }

    /**
     * Nested DTO for individual round details.
     * Contains round metadata and all votes cast in that round.
     */
    public static class RoundDetailDTO {

        /**
         * Sequential round number within the session.
         */
        @JsonProperty("round_number")
        private Integer roundNumber;

        /**
         * Story title being estimated (may be null for quick estimates).
         */
        @JsonProperty("story_title")
        private String storyTitle;

        /**
         * All votes cast in this round, ordered by vote time.
         */
        @JsonProperty("votes")
        private List<VoteDetailDTO> votes;

        /**
         * Calculated average of numeric votes (null if no numeric votes).
         */
        @JsonProperty("average")
        private BigDecimal average;

        /**
         * Calculated median card value (may be non-numeric like "?").
         */
        @JsonProperty("median")
        private String median;

        /**
         * Whether all participants voted the same value.
         */
        @JsonProperty("consensus_reached")
        private Boolean consensusReached;

        /**
         * Round start timestamp (UTC).
         */
        @JsonProperty("started_at")
        private Instant startedAt;

        /**
         * Round reveal timestamp (UTC), null if not yet revealed.
         */
        @JsonProperty("revealed_at")
        private Instant revealedAt;

        /**
         * Default constructor for Jackson deserialization.
         */
        public RoundDetailDTO() {
        }

        /**
         * Constructs a RoundDetailDTO with all fields.
         *
         * @param roundNumber Round number
         * @param storyTitle Story title
         * @param votes Vote details list
         * @param average Average vote value
         * @param median Median vote value
         * @param consensusReached Consensus flag
         * @param startedAt Round start time
         * @param revealedAt Round reveal time
         */
        public RoundDetailDTO(final Integer roundNumber,
                              final String storyTitle,
                              final List<VoteDetailDTO> votes,
                              final BigDecimal average,
                              final String median,
                              final Boolean consensusReached,
                              final Instant startedAt,
                              final Instant revealedAt) {
            this.roundNumber = roundNumber;
            this.storyTitle = storyTitle;
            this.votes = votes;
            this.average = average;
            this.median = median;
            this.consensusReached = consensusReached;
            this.startedAt = startedAt;
            this.revealedAt = revealedAt;
        }

        // Getters and setters

        public Integer getRoundNumber() {
            return roundNumber;
        }

        public void setRoundNumber(final Integer roundNumber) {
            this.roundNumber = roundNumber;
        }

        public String getStoryTitle() {
            return storyTitle;
        }

        public void setStoryTitle(final String storyTitle) {
            this.storyTitle = storyTitle;
        }

        public List<VoteDetailDTO> getVotes() {
            return votes;
        }

        public void setVotes(final List<VoteDetailDTO> votes) {
            this.votes = votes;
        }

        public BigDecimal getAverage() {
            return average;
        }

        public void setAverage(final BigDecimal average) {
            this.average = average;
        }

        public String getMedian() {
            return median;
        }

        public void setMedian(final String median) {
            this.median = median;
        }

        public Boolean getConsensusReached() {
            return consensusReached;
        }

        public void setConsensusReached(final Boolean consensusReached) {
            this.consensusReached = consensusReached;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(final Instant startedAt) {
            this.startedAt = startedAt;
        }

        public Instant getRevealedAt() {
            return revealedAt;
        }

        public void setRevealedAt(final Instant revealedAt) {
            this.revealedAt = revealedAt;
        }
    }

    /**
     * Nested DTO for individual vote details.
     * Represents a single participant's vote in a round.
     */
    public static class VoteDetailDTO {

        /**
         * Participant UUID.
         */
        @JsonProperty("participant_id")
        private UUID participantId;

        /**
         * Participant display name at time of vote.
         */
        @JsonProperty("participant_name")
        private String participantName;

        /**
         * Card value voted (numeric or special: ?, ∞, ☕).
         */
        @JsonProperty("card_value")
        private String cardValue;

        /**
         * Vote timestamp (UTC).
         */
        @JsonProperty("voted_at")
        private Instant votedAt;

        /**
         * Default constructor for Jackson deserialization.
         */
        public VoteDetailDTO() {
        }

        /**
         * Constructs a VoteDetailDTO with all fields.
         *
         * @param participantId Participant UUID
         * @param participantName Participant display name
         * @param cardValue Card value
         * @param votedAt Vote timestamp
         */
        public VoteDetailDTO(final UUID participantId,
                             final String participantName,
                             final String cardValue,
                             final Instant votedAt) {
            this.participantId = participantId;
            this.participantName = participantName;
            this.cardValue = cardValue;
            this.votedAt = votedAt;
        }

        // Getters and setters

        public UUID getParticipantId() {
            return participantId;
        }

        public void setParticipantId(final UUID participantId) {
            this.participantId = participantId;
        }

        public String getParticipantName() {
            return participantName;
        }

        public void setParticipantName(final String participantName) {
            this.participantName = participantName;
        }

        public String getCardValue() {
            return cardValue;
        }

        public void setCardValue(final String cardValue) {
            this.cardValue = cardValue;
        }

        public Instant getVotedAt() {
            return votedAt;
        }

        public void setVotedAt(final Instant votedAt) {
            this.votedAt = votedAt;
        }
    }
}
