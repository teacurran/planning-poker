package com.terrencecurran.planningpoker.dto;

import java.util.List;

public class RoomState {
    public String roomId;
    public String roomName;
    public List<PlayerState> players;
    public boolean isVotingActive;
    public boolean areCardsRevealed;
    public Integer currentRound;
    public VotingStats votingStats;
    
    public static class PlayerState {
        public String id;
        public String username;
        public boolean isObserver;
        public boolean isModerator;
        public boolean hasVoted;
        public String vote;
        public boolean isConnected;
    }
    
    public static class VotingStats {
        public Integer totalPlayers;
        public Integer votedPlayers;
        public String average;
        public String consensus;
        public List<VoteCount> voteCounts;
    }
    
    public static class VoteCount {
        public String value;
        public Integer count;
    }
}