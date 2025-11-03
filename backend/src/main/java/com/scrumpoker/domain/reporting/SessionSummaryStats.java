package com.scrumpoker.domain.reporting;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * POJO for session summary statistics stored in SessionHistory JSONB field.
 * Contains aggregate metrics calculated from all rounds in a session.
 */
public class SessionSummaryStats {

    @JsonProperty("total_votes")
    private Integer totalVotes;

    @JsonProperty("consensus_rate")
    private BigDecimal consensusRate;

    @JsonProperty("avg_estimation_time_seconds")
    private Long avgEstimationTimeSeconds;

    @JsonProperty("rounds_with_consensus")
    private Integer roundsWithConsensus;

    public SessionSummaryStats() {
    }

    public SessionSummaryStats(Integer totalVotes, BigDecimal consensusRate, Long avgEstimationTimeSeconds, Integer roundsWithConsensus) {
        this.totalVotes = totalVotes;
        this.consensusRate = consensusRate;
        this.avgEstimationTimeSeconds = avgEstimationTimeSeconds;
        this.roundsWithConsensus = roundsWithConsensus;
    }

    public Integer getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(Integer totalVotes) {
        this.totalVotes = totalVotes;
    }

    public BigDecimal getConsensusRate() {
        return consensusRate;
    }

    public void setConsensusRate(BigDecimal consensusRate) {
        this.consensusRate = consensusRate;
    }

    public Long getAvgEstimationTimeSeconds() {
        return avgEstimationTimeSeconds;
    }

    public void setAvgEstimationTimeSeconds(Long avgEstimationTimeSeconds) {
        this.avgEstimationTimeSeconds = avgEstimationTimeSeconds;
    }

    public Integer getRoundsWithConsensus() {
        return roundsWithConsensus;
    }

    public void setRoundsWithConsensus(Integer roundsWithConsensus) {
        this.roundsWithConsensus = roundsWithConsensus;
    }
}
