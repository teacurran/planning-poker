package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * POJO for session summary statistics stored in SessionHistory JSONB.
 * Contains aggregate metrics calculated from all rounds in a session.
 *
 * <p>This class is immutable after construction and thread-safe.
 * Used for serializing summary statistics to JSONB format in PostgreSQL.
 */
public final class SessionSummaryStats {

    /**
     * Total number of votes cast across all rounds in the session.
     */
    @JsonProperty("total_votes")
    private Integer totalVotes;

    /**
     * Percentage of rounds that reached consensus (0.0 to 1.0).
     */
    @JsonProperty("consensus_rate")
    private BigDecimal consensusRate;

    /**
     * Average time in seconds spent per estimation round.
     */
    @JsonProperty("avg_estimation_time_seconds")
    private Long avgEstimationTimeSeconds;

    /**
     * Count of rounds that achieved consensus.
     */
    @JsonProperty("rounds_with_consensus")
    private Integer roundsWithConsensus;

    /**
     * Default constructor for Jackson deserialization.
     */
    public SessionSummaryStats() {
    }

    /**
     * Constructs a new SessionSummaryStats with all fields.
     *
     * @param votes Total vote count
     * @param consensus Consensus rate
     * @param avgTime Average estimation time in seconds
     * @param consensusCount Rounds with consensus count
     */
    public SessionSummaryStats(final Integer votes,
                               final BigDecimal consensus,
                               final Long avgTime,
                               final Integer consensusCount) {
        this.totalVotes = votes;
        this.consensusRate = consensus;
        this.avgEstimationTimeSeconds = avgTime;
        this.roundsWithConsensus = consensusCount;
    }

    /**
     * Gets the total votes count.
     *
     * @return The total number of votes
     */
    public Integer getTotalVotes() {
        return totalVotes;
    }

    /**
     * Sets the total votes count.
     *
     * @param votes The total votes to set
     */
    public void setTotalVotes(final Integer votes) {
        this.totalVotes = votes;
    }

    /**
     * Gets the consensus rate.
     *
     * @return The consensus rate as a decimal (0.0 to 1.0)
     */
    public BigDecimal getConsensusRate() {
        return consensusRate;
    }

    /**
     * Sets the consensus rate.
     *
     * @param consensus The consensus rate to set
     */
    public void setConsensusRate(final BigDecimal consensus) {
        this.consensusRate = consensus;
    }

    /**
     * Gets the average estimation time.
     *
     * @return The average time in seconds
     */
    public Long getAvgEstimationTimeSeconds() {
        return avgEstimationTimeSeconds;
    }

    /**
     * Sets the average estimation time.
     *
     * @param avgTime The average time to set
     */
    public void setAvgEstimationTimeSeconds(final Long avgTime) {
        this.avgEstimationTimeSeconds = avgTime;
    }

    /**
     * Gets the count of rounds with consensus.
     *
     * @return The number of rounds that reached consensus
     */
    public Integer getRoundsWithConsensus() {
        return roundsWithConsensus;
    }

    /**
     * Sets the count of rounds with consensus.
     *
     * @param consensusCount The consensus count to set
     */
    public void setRoundsWithConsensus(final Integer consensusCount) {
        this.roundsWithConsensus = consensusCount;
    }
}
