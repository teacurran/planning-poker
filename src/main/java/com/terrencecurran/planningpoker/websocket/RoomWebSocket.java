package com.terrencecurran.planningpoker.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terrencecurran.planningpoker.entity.Player;
import com.terrencecurran.planningpoker.entity.Room;
import com.terrencecurran.planningpoker.entity.Vote;
import com.terrencecurran.planningpoker.service.RoomService;
import com.terrencecurran.planningpoker.websocket.message.*;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ServerEndpoint("/ws/room/{roomId}")
@ApplicationScoped
public class RoomWebSocket {
    
    private static final Logger LOGGER = Logger.getLogger(RoomWebSocket.class.getName());
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToPlayer = new ConcurrentHashMap<>();
    
    @Inject
    RoomService roomService;
    
    @Inject
    ObjectMapper objectMapper;
    
    @OnOpen
    public void onOpen(Session session, @PathParam("roomId") String roomId) {
        sessions.put(session.getId(), session);
        LOGGER.info("WebSocket opened for room: " + roomId + ", session: " + session.getId());
    }
    
    @OnMessage
    public void onMessage(String message, Session session, @PathParam("roomId") String roomId) {
        try {
            BaseMessage baseMessage = objectMapper.readValue(message, BaseMessage.class);
            
            switch (baseMessage.type) {
                case "JOIN_ROOM":
                    handleJoinRoom(session, roomId, objectMapper.readValue(message, JoinRoomMessage.class));
                    break;
                case "VOTE":
                    handleVote(session, roomId, objectMapper.readValue(message, VoteMessage.class));
                    break;
                case "REVEAL_CARDS":
                    handleRevealCards(roomId);
                    break;
                case "RESET_VOTES":
                    handleResetVotes(roomId);
                    break;
                case "TOGGLE_OBSERVER":
                    handleToggleObserver(session, roomId);
                    break;
            }
        } catch (Exception e) {
            LOGGER.severe("Error processing message: " + e.getMessage());
        }
    }
    
    @OnClose
    public void onClose(Session session, @PathParam("roomId") String roomId) {
        sessions.remove(session.getId());
        String playerId = sessionToPlayer.remove(session.getId());
        
        if (playerId != null) {
            roomService.disconnectPlayer(playerId)
                .subscribe().with(
                    result -> broadcastRoomState(roomId),
                    error -> LOGGER.severe("Error disconnecting player: " + error.getMessage())
                );
        }
    }
    
    @OnError
    public void onError(Session session, @PathParam("roomId") String roomId, Throwable throwable) {
        LOGGER.severe("WebSocket error for room " + roomId + ": " + throwable.getMessage());
        sessions.remove(session.getId());
    }
    
    private void handleJoinRoom(Session session, String roomId, JoinRoomMessage message) {
        roomService.joinRoom(roomId, message.username, session.getId())
            .subscribe().with(
                player -> {
                    sessionToPlayer.put(session.getId(), player.id);
                    broadcastRoomState(roomId);
                },
                error -> sendError(session, "Failed to join room: " + error.getMessage())
            );
    }
    
    private void handleVote(Session session, String roomId, VoteMessage message) {
        String playerId = sessionToPlayer.get(session.getId());
        if (playerId == null) {
            sendError(session, "You must join the room first");
            return;
        }
        
        roomService.castVote(roomId, playerId, message.value)
            .subscribe().with(
                vote -> broadcastRoomState(roomId),
                error -> sendError(session, "Failed to cast vote: " + error.getMessage())
            );
    }
    
    private void handleRevealCards(String roomId) {
        roomService.revealCards(roomId)
            .subscribe().with(
                room -> broadcastRoomState(roomId),
                error -> LOGGER.severe("Failed to reveal cards: " + error.getMessage())
            );
    }
    
    private void handleResetVotes(String roomId) {
        roomService.resetVotes(roomId)
            .subscribe().with(
                room -> broadcastRoomState(roomId),
                error -> LOGGER.severe("Failed to reset votes: " + error.getMessage())
            );
    }
    
    private void handleToggleObserver(Session session, String roomId) {
        String playerId = sessionToPlayer.get(session.getId());
        if (playerId == null) {
            sendError(session, "You must join the room first");
            return;
        }
        
        roomService.toggleObserver(playerId)
            .subscribe().with(
                player -> broadcastRoomState(roomId),
                error -> sendError(session, "Failed to toggle observer: " + error.getMessage())
            );
    }
    
    private void broadcastRoomState(String roomId) {
        roomService.getRoomState(roomId)
            .subscribe().with(
                roomState -> {
                    String stateJson = serializeMessage(new RoomStateMessage(roomState));
                    sessions.values().forEach(session -> {
                        if (session.isOpen()) {
                            session.getAsyncRemote().sendText(stateJson);
                        }
                    });
                },
                error -> LOGGER.severe("Failed to broadcast room state: " + error.getMessage())
            );
    }
    
    private void sendError(Session session, String errorMessage) {
        if (session.isOpen()) {
            ErrorMessage error = new ErrorMessage(errorMessage);
            session.getAsyncRemote().sendText(serializeMessage(error));
        }
    }
    
    private String serializeMessage(Object message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            LOGGER.severe("Failed to serialize message: " + e.getMessage());
            return "{}";
        }
    }
}