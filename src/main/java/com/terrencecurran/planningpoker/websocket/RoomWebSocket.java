package com.terrencecurran.planningpoker.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terrencecurran.planningpoker.service.RoomService;
import com.terrencecurran.planningpoker.websocket.message.*;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnError;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WebSocket endpoint for Planning Poker rooms using Quarkus WebSockets Next.
 * This handles all real-time communication between clients and the server.
 */
@WebSocket(path = "/ws/room/{roomId}")
@ApplicationScoped
public class RoomWebSocket {
    
    private static final Logger LOGGER = Logger.getLogger(RoomWebSocket.class.getName());
    
    // Maps to track connections and their associated players/rooms
    private final Map<String, String> connectionToPlayer = new ConcurrentHashMap<>();
    private final Map<String, String> connectionToRoom = new ConcurrentHashMap<>();
    private final Map<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
    
    @Inject
    RoomService roomService;
    
    @Inject
    ObjectMapper objectMapper;
    
    @OnOpen
    public Uni<Void> onOpen(WebSocketConnection connection) {
        String roomId = connection.pathParam("roomId");
        String connectionId = connection.id();
        
        LOGGER.info("WebSocket opened for room: " + roomId + ", connection: " + connectionId);
        
        // Store the connection
        connections.put(connectionId, connection);
        connectionToRoom.put(connectionId, roomId);
        
        // Send initial room state - wrap in session
        return Panache.withSession(() -> 
            roomService.getRoomState(roomId)
                .flatMap(roomState -> {
                    if (roomState != null) {
                        try {
                            String stateJson = objectMapper.writeValueAsString(new RoomStateMessage(roomState));
                            LOGGER.info("Sending initial room state to connection: " + connectionId);
                            return connection.sendText(stateJson);
                        } catch (JsonProcessingException e) {
                            LOGGER.severe("Failed to serialize room state: " + e.getMessage());
                            return Uni.createFrom().voidItem();
                        }
                    }
                    return Uni.createFrom().voidItem();
                })
        ).onFailure().recoverWithItem((Void) null);
    }
    
    @OnTextMessage
    public Uni<Void> onMessage(WebSocketConnection connection, String message) {
        String roomId = connection.pathParam("roomId");
        String connectionId = connection.id();
        
        LOGGER.info("Received message from connection " + connectionId + ": " + message);
        
        try {
            BaseMessage baseMessage = objectMapper.readValue(message, BaseMessage.class);
            LOGGER.info("Message type: " + baseMessage.type);
            
            return switch (baseMessage.type) {
                case "JOIN_ROOM" -> {
                    JoinRoomMessage joinMsg = objectMapper.readValue(message, JoinRoomMessage.class);
                    yield handleJoinRoom(connection, roomId, joinMsg);
                }
                case "VOTE" -> {
                    VoteMessage voteMsg = objectMapper.readValue(message, VoteMessage.class);
                    yield handleVote(connection, roomId, voteMsg);
                }
                case "REVEAL_CARDS" -> handleRevealCards(roomId);
                case "HIDE_CARDS" -> handleHideCards(roomId);
                case "RESET_VOTES" -> handleResetVotes(roomId);
                case "TOGGLE_OBSERVER" -> handleToggleObserver(connection, roomId);
                default -> {
                    LOGGER.warning("Unknown message type: " + baseMessage.type);
                    yield Uni.createFrom().voidItem();
                }
            };
        } catch (Exception e) {
            LOGGER.severe("Error processing message: " + e.getMessage());
            e.printStackTrace();
            return sendError(connection, "Failed to process message: " + e.getMessage());
        }
    }
    
    @OnClose
    public Uni<Void> onClose(WebSocketConnection connection) {
        String connectionId = connection.id();
        String roomId = connectionToRoom.remove(connectionId);
        String playerId = connectionToPlayer.remove(connectionId);
        connections.remove(connectionId);
        
        LOGGER.info("WebSocket closed for connection: " + connectionId);
        
        if (playerId != null && roomId != null) {
            return Panache.withSession(() ->
                roomService.disconnectPlayer(playerId)
                    .flatMap(result -> broadcastRoomState(roomId))
            ).onFailure().invoke(error -> 
                LOGGER.severe("Error disconnecting player: " + error.getMessage())
            ).replaceWithVoid();
        }
        
        return Uni.createFrom().voidItem();
    }
    
    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        String connectionId = connection.id();
        LOGGER.severe("WebSocket error for connection " + connectionId + ": " + throwable.getMessage());
        throwable.printStackTrace();
    }
    
    private Uni<Void> handleJoinRoom(WebSocketConnection connection, String roomId, JoinRoomMessage message) {
        String connectionId = connection.id();
        LOGGER.info("Handling join room for connection: " + connectionId + ", username: " + message.username);
        
        return Panache.withSession(() ->
            roomService.joinRoom(roomId, message.username, connectionId)
                .flatMap(player -> {
                    if (player != null && player.id != null) {
                        LOGGER.info("Player joined successfully: " + player.id + " for connection: " + connectionId);
                        LOGGER.info("  Username: " + player.username);
                        LOGGER.info("  Is Moderator: " + player.isModerator);
                        LOGGER.info("  Is Observer: " + player.isObserver);
                        LOGGER.info("  Is Connected: " + player.isConnected);
                        
                        connectionToPlayer.put(connectionId, player.id);
                        LOGGER.info("Added mapping - connection: " + connectionId + " -> player: " + player.id);
                        
                        return broadcastRoomState(roomId);
                    } else {
                        LOGGER.severe("Player or player.id is null after join");
                        return sendError(connection, "Failed to join room: player creation failed");
                    }
                })
        ).onFailure().recoverWithUni(error -> {
            LOGGER.severe("Exception in join room: " + error.getMessage());
            error.printStackTrace();
            return sendError(connection, "Failed to join room: " + error.getMessage());
        });
    }
    
    private Uni<Void> handleVote(WebSocketConnection connection, String roomId, VoteMessage message) {
        String connectionId = connection.id();
        String playerId = connectionToPlayer.get(connectionId);
        
        LOGGER.info("Handling vote for connection: " + connectionId + ", playerId: " + playerId + ", value: " + message.value);
        
        if (playerId == null) {
            LOGGER.warning("No player mapping found for connection: " + connectionId);
            return sendError(connection, "You must join the room first");
        }
        
        return Panache.withSession(() ->
            roomService.castVote(roomId, playerId, message.value)
                .flatMap(vote -> {
                    if (vote != null) {
                        LOGGER.info("Vote cast successfully for player: " + playerId);
                        return broadcastRoomState(roomId);
                    } else {
                        LOGGER.warning("Vote returned null for player: " + playerId);
                        return sendError(connection, "Failed to cast vote: invalid vote");
                    }
                })
        ).onFailure().recoverWithUni(error -> {
            LOGGER.severe("Failed to cast vote: " + error.getMessage());
            error.printStackTrace();
            return sendError(connection, "Failed to cast vote: " + error.getMessage());
        });
    }
    
    private Uni<Void> handleRevealCards(String roomId) {
        LOGGER.info("Handling reveal cards for room: " + roomId);
        return Panache.withSession(() ->
            roomService.revealCards(roomId)
                .flatMap(room -> broadcastRoomState(roomId))
        ).onFailure().invoke(error -> 
            LOGGER.severe("Failed to reveal cards: " + error.getMessage())
        ).replaceWithVoid();
    }
    
    private Uni<Void> handleHideCards(String roomId) {
        LOGGER.info("Handling hide cards for room: " + roomId);
        return Panache.withSession(() ->
            roomService.hideCards(roomId)
                .flatMap(room -> broadcastRoomState(roomId))
        ).onFailure().invoke(error -> 
            LOGGER.severe("Failed to hide cards: " + error.getMessage())
        ).replaceWithVoid();
    }
    
    private Uni<Void> handleResetVotes(String roomId) {
        LOGGER.info("Handling reset votes for room: " + roomId);
        return Panache.withSession(() ->
            roomService.resetVotes(roomId)
                .flatMap(room -> broadcastRoomState(roomId))
        ).onFailure().invoke(error -> 
            LOGGER.severe("Failed to reset votes: " + error.getMessage())
        ).replaceWithVoid();
    }
    
    private Uni<Void> handleToggleObserver(WebSocketConnection connection, String roomId) {
        String connectionId = connection.id();
        String playerId = connectionToPlayer.get(connectionId);
        
        if (playerId == null) {
            return sendError(connection, "You must join the room first");
        }
        
        return Panache.withSession(() ->
            roomService.toggleObserver(playerId)
                .flatMap(player -> broadcastRoomState(roomId))
        ).onFailure().recoverWithUni(error -> 
            sendError(connection, "Failed to toggle observer: " + error.getMessage())
        );
    }
    
    private Uni<Void> broadcastRoomState(String roomId) {
        LOGGER.info("Broadcasting room state for room: " + roomId);
        
        // Note: broadcastRoomState is already called within a session context from the handlers
        // so we don't need to wrap it again here
        return roomService.getRoomState(roomId)
            .flatMap(roomState -> {
                if (roomState != null) {
                    LOGGER.info("Room state retrieved - players count: " + 
                        (roomState.players != null ? roomState.players.size() : 0));
                    
                    if (roomState.players != null) {
                        roomState.players.forEach(p -> 
                            LOGGER.info("  Player: " + p.username + 
                                " (id: " + p.id + 
                                ", moderator: " + p.isModerator + 
                                ", connected: " + p.isConnected + ")"));
                    }
                    
                    try {
                        String stateJson = objectMapper.writeValueAsString(new RoomStateMessage(roomState));
                        
                        // Find all connections in this room
                        var roomConnections = connectionToRoom.entrySet().stream()
                            .filter(entry -> roomId.equals(entry.getValue()))
                            .map(entry -> connections.get(entry.getKey()))
                            .filter(conn -> conn != null && conn.isOpen())
                            .toList();
                        
                        LOGGER.info("Broadcasting to " + roomConnections.size() + " connections in room " + roomId);
                        
                        // Send to all connections in parallel
                        if (!roomConnections.isEmpty()) {
                            return Uni.join().all(
                                roomConnections.stream()
                                    .map(conn -> {
                                        LOGGER.info("Sending to connection: " + conn.id());
                                        return conn.sendText(stateJson)
                                            .onFailure().invoke(error -> 
                                                LOGGER.warning("Failed to send to connection " + conn.id() + ": " + error.getMessage())
                                            );
                                    })
                                    .toList()
                            ).andFailFast().replaceWithVoid();
                        }
                    } catch (JsonProcessingException e) {
                        LOGGER.severe("Failed to serialize room state: " + e.getMessage());
                    }
                } else {
                    LOGGER.warning("Room state is null for room: " + roomId);
                }
                return Uni.createFrom().voidItem();
            })
            .onFailure().invoke(error -> {
                LOGGER.severe("Failed to broadcast room state: " + error.getMessage());
                error.printStackTrace();
            })
            .replaceWithVoid();
    }
    
    private Uni<Void> sendError(WebSocketConnection connection, String errorMessage) {
        try {
            ErrorMessage error = new ErrorMessage(errorMessage);
            String errorJson = objectMapper.writeValueAsString(error);
            return connection.sendText(errorJson);
        } catch (JsonProcessingException e) {
            LOGGER.severe("Failed to serialize error message: " + e.getMessage());
            return Uni.createFrom().voidItem();
        }
    }
}