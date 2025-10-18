package com.scrumpoker.api.websocket.handler;

import com.scrumpoker.api.websocket.ConnectionRegistry;
import com.scrumpoker.api.websocket.WebSocketMessage;
import com.scrumpoker.domain.room.RoomParticipant;
import com.scrumpoker.domain.room.RoomRole;
import com.scrumpoker.domain.room.VotingService;
import com.scrumpoker.repository.RoomParticipantRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handles round.start.v1 messages (host-only).
 * <p>
 * Processes round start requests from the host, validates authorization,
 * and delegates to VotingService.
 * </p>
 */
@ApplicationScoped
public class RoundStartHandler implements MessageHandler {

    @Inject
    VotingService votingService;

    @Inject
    RoomParticipantRepository participantRepository;

    @Inject
    ConnectionRegistry connectionRegistry;

    @Override
    public String getMessageType() {
        return "round.start.v1";
    }

    @Override
    public Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId) {
        String requestId = message.getRequestId();
        Map<String, Object> payload = message.getPayload();

        // Extract optional fields
        String storyTitle = null;
        try {
            storyTitle = extractString(payload, "storyTitle", false);
        } catch (IllegalArgumentException e) {
            sendError(session, requestId, 4004, "VALIDATION_ERROR", e.getMessage());
            return Uni.createFrom().voidItem();
        }

        final String finalStoryTitle = storyTitle;

        // Verify host authorization
        return verifyHostRole(userId, roomId)
                .onItem().transformToUni(isHost -> {
                    if (!isHost) {
                        sendError(session, requestId, 4003, "FORBIDDEN",
                                "Only HOST role can start rounds");
                        return Uni.createFrom().voidItem();
                    }

                    // Start round via VotingService
                    return votingService.startRound(roomId, finalStoryTitle)
                            .onItem().transformToUni(round -> {
                                Log.infof("Round started successfully: roundId=%s, roomId=%s, storyTitle=%s",
                                        round.roundId, roomId, finalStoryTitle);
                                return Uni.createFrom().voidItem();
                            })
                            .onFailure(IllegalArgumentException.class).recoverWithUni(e -> {
                                sendError(session, requestId, 4005, "INVALID_STATE", e.getMessage());
                                return Uni.createFrom().voidItem();
                            })
                            .onFailure().recoverWithUni(e -> {
                                Log.errorf(e, "Failed to start round: %s", e.getMessage());
                                sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR",
                                        "Failed to start round");
                                return Uni.createFrom().voidItem();
                            });
                })
                .onFailure().recoverWithUni(e -> {
                    Log.errorf(e, "Failed to handle round.start.v1: %s", e.getMessage());
                    sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR",
                            "Failed to process round start");
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * Verifies if the user has HOST role in the room.
     */
    private Uni<Boolean> verifyHostRole(String userId, String roomId) {
        UUID userUuid = UUID.fromString(userId);

        return participantRepository.find(
                "room.roomId = ?1 and user.userId = ?2",
                roomId,
                userUuid
        ).firstResult()
                .onItem().transform(participant -> {
                    if (participant == null) {
                        return false;
                    }
                    return participant.role == RoomRole.HOST;
                });
    }

    /**
     * Extracts a string value from the payload.
     */
    private String extractString(Map<String, Object> payload, String key, boolean required) {
        Object value = payload.get(key);

        if (value == null) {
            if (required) {
                throw new IllegalArgumentException(key + " is required");
            }
            return null;
        }

        if (!(value instanceof String)) {
            throw new IllegalArgumentException(key + " must be a string");
        }

        String stringValue = (String) value;

        if (required && stringValue.trim().isEmpty()) {
            throw new IllegalArgumentException(key + " cannot be empty");
        }

        return stringValue.trim();
    }

    /**
     * Sends an error message to the client.
     */
    private void sendError(Session session, String requestId, int code, String error, String message) {
        WebSocketMessage errorMessage = WebSocketMessage.createError(
                requestId, code, error, message, Instant.now().toString()
        );
        connectionRegistry.sendToSession(session, errorMessage);
    }
}
