package com.terrencecurran.planningpoker.service;

import com.terrencecurran.planningpoker.dto.RoomState;
import com.terrencecurran.planningpoker.entity.Player;
import com.terrencecurran.planningpoker.entity.Room;
import com.terrencecurran.planningpoker.entity.Vote;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class RoomService {
    
    public Uni<Room> createRoom(String name) {
        return Panache.withTransaction(() -> {
            Room room = new Room();
            room.name = name;
            room.isVotingActive = true;
            room.areCardsRevealed = false;
            return room.persist();
        });
    }
    
    public Uni<Room> getRoom(String roomId) {
        return Room.<Room>findById(roomId);
    }
    
    public Uni<Player> joinRoom(String roomId, String username, String sessionId) {
        return Panache.withTransaction(() -> 
            Room.<Room>findById(roomId)
                .onItem().ifNotNull().transformToUni(room -> {
                    // First check if player already exists for this session AND room
                    return Player.<Player>find("sessionId = ?1 and room.id = ?2", sessionId, roomId)
                        .firstResult()
                        .onItem().transformToUni(existingPlayer -> {
                            if (existingPlayer != null) {
                                // Reconnect existing player
                                existingPlayer.isConnected = true;
                                existingPlayer.username = username; // Update username in case it changed
                                return existingPlayer.<Player>persist();
                            } else {
                                // Create new player
                                Player player = new Player();
                                player.username = username;
                                player.sessionId = sessionId;
                                player.room = room;
                                player.isObserver = false;
                                player.isConnected = true;
                                
                                return player.<Player>persistAndFlush();
                            }
                        });
                })
        );
    }
    
    public Uni<Vote> castVote(String roomId, String playerId, String value) {
        return Panache.withTransaction(() ->
            Uni.combine().all()
                .unis(
                    Room.<Room>findById(roomId),
                    Player.<Player>findById(playerId)
                )
                .asTuple()
                .onItem().transformToUni(tuple -> {
                    Room room = tuple.getItem1();
                    Player player = tuple.getItem2();
                    
                    if (room == null || player == null || player.isObserver) {
                        return Uni.createFrom().nullItem();
                    }
                    
                    return Vote.<Vote>find("room = ?1 and player = ?2 and votingRound = ?3", 
                            room, player, getCurrentRound(room))
                        .firstResult()
                        .onItem().transformToUni(existingVote -> {
                            Vote vote = existingVote != null ? existingVote : new Vote();
                            vote.room = room;
                            vote.player = player;
                            vote.value = value;
                            vote.votingRound = getCurrentRound(room);
                            return vote.persist();
                        });
                })
        );
    }
    
    public Uni<Room> revealCards(String roomId) {
        return Panache.withTransaction(() ->
            Room.<Room>findById(roomId)
                .onItem().ifNotNull().transformToUni(room -> {
                    room.areCardsRevealed = true;
                    return room.persist();
                })
        );
    }
    
    public Uni<Room> hideCards(String roomId) {
        return Panache.withTransaction(() ->
            Room.<Room>findById(roomId)
                .onItem().ifNotNull().transformToUni(room -> {
                    room.areCardsRevealed = false;
                    return room.persist();
                })
        );
    }
    
    public Uni<Room> resetVotes(String roomId) {
        return Panache.withTransaction(() ->
            Room.<Room>findById(roomId)
                .onItem().ifNotNull().transformToUni(room -> {
                    room.areCardsRevealed = false;
                    room.isVotingActive = true;
                    
                    return Vote.<Vote>delete("room = ?1 and votingRound = ?2", 
                            room, getCurrentRound(room))
                        .onItem().transformToUni(deleted -> room.<Room>persist());
                })
        );
    }
    
    public Uni<Player> toggleObserver(String playerId) {
        return Panache.withTransaction(() ->
            Player.<Player>findById(playerId)
                .onItem().ifNotNull().transformToUni(player -> {
                    player.isObserver = !player.isObserver;
                    return player.persist();
                })
        );
    }
    
    public Uni<Player> disconnectPlayer(String playerId) {
        return Panache.withTransaction(() ->
            Player.<Player>findById(playerId)
                .onItem().ifNotNull().transformToUni(player -> {
                    player.isConnected = false;
                    return player.persist();
                })
        );
    }
    
    public Uni<RoomState> getRoomState(String roomId) {
        // Wrap in session for read operations when called from outside a transaction
        return Panache.withSession(() -> getRoomStateInternal(roomId));
    }
    
    // Internal version that doesn't wrap in session - for use within existing transactions
    private Uni<RoomState> getRoomStateInternal(String roomId) {
        return Room.<Room>findById(roomId)
            .onItem().transformToUni(room -> {
                if (room == null) {
                    return Uni.createFrom().nullItem();
                }
                
                // Get all players who have ever joined this room
                return Player.<Player>list("room.id = ?1 ORDER BY joinedAt", roomId)
                    .onItem().transformToUni(players -> 
                        Vote.<Vote>list("room = ?1 and votingRound = ?2", 
                                room, getCurrentRound(room))
                            .onItem().transform(votes -> buildRoomState(room, players, votes))
                    );
            });
    }
    
    private RoomState buildRoomState(Room room, List<Player> players, List<Vote> votes) {
        RoomState state = new RoomState();
        state.roomId = room.id;
        state.roomName = room.name;
        state.isVotingActive = room.isVotingActive;
        state.areCardsRevealed = room.areCardsRevealed;
        state.currentRound = getCurrentRound(room);
        
        Map<String, Vote> playerVotes = votes.stream()
            .collect(Collectors.toMap(v -> v.player.id, v -> v));
        
        state.players = players.stream().map(player -> {
            RoomState.PlayerState ps = new RoomState.PlayerState();
            ps.id = player.id;
            ps.username = player.username;
            ps.isObserver = player.isObserver;
            ps.isConnected = player.isConnected;
            
            Vote vote = playerVotes.get(player.id);
            ps.hasVoted = vote != null;
            ps.vote = (vote != null && room.areCardsRevealed) ? vote.value : null;
            
            return ps;
        }).collect(Collectors.toList());
        
        if (room.areCardsRevealed) {
            state.votingStats = calculateVotingStats(players, votes);
        }
        
        return state;
    }
    
    private RoomState.VotingStats calculateVotingStats(List<Player> players, List<Vote> votes) {
        RoomState.VotingStats stats = new RoomState.VotingStats();
        
        List<Player> votingPlayers = players.stream()
            .filter(p -> !p.isObserver)
            .collect(Collectors.toList());
        
        stats.totalPlayers = votingPlayers.size();
        stats.votedPlayers = votes.size();
        
        Map<String, Integer> voteCountMap = new HashMap<>();
        for (Vote vote : votes) {
            voteCountMap.merge(vote.value, 1, Integer::sum);
        }
        
        stats.voteCounts = voteCountMap.entrySet().stream()
            .map(entry -> {
                RoomState.VoteCount vc = new RoomState.VoteCount();
                vc.value = entry.getKey();
                vc.count = entry.getValue();
                return vc;
            })
            .collect(Collectors.toList());
        
        List<Integer> numericVotes = votes.stream()
            .map(v -> {
                try {
                    return Integer.parseInt(v.value);
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .filter(v -> v != null)
            .collect(Collectors.toList());
        
        if (!numericVotes.isEmpty()) {
            double average = numericVotes.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0.0);
            stats.average = String.format("%.1f", average);
        }
        
        if (voteCountMap.size() == 1) {
            stats.consensus = voteCountMap.keySet().iterator().next();
        }
        
        return stats;
    }
    
    // For use when broadcasting from within a transaction context
    public Uni<RoomState> getRoomStateForBroadcast(String roomId) {
        return getRoomStateInternal(roomId);
    }
    
    private Integer getCurrentRound(Room room) {
        return 1;
    }
}