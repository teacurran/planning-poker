package com.scrumpoker.api.websocket.handler;

import com.scrumpoker.api.websocket.ConnectionRegistry;
import com.scrumpoker.api.websocket.WebSocketMessage;
import com.scrumpoker.domain.room.Round;
import com.scrumpoker.domain.room.RoomParticipant;
import com.scrumpoker.domain.room.VotingService;
import com.scrumpoker.repository.RoomParticipantRepository;
import com.scrumpoker.repository.RoundRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handles vote.cast.v1 messages.
 * <p>
 * Processes vote casting requests from participants, validates the card value,
 * and delegates to VotingService for persistence.
 * </p>
 */
@ApplicationScoped
public class VoteCastHandler implements MessageHandler {

    @Inject
    VotingService votingService;

    @Inject
    RoomParticipantRepository participantRepository;

    @Inject
    RoundRepository roundRepository;

    @Inject
    ConnectionRegistry connectionRegistry;

    @Override
    public String getMessageType() {
        return "vote.cast.v1";
    }

    @Override
    public Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId) {
        String requestId = message.getRequestId();
        Map<String, Object> payload = message.getPayload();

        // Extract and validate cardValue
        String cardValue;
        try {
            cardValue = extractString(payload, "cardValue", true);
        } catch (IllegalArgumentException e) {
            sendError(session, requestId, 4004, "VALIDATION_ERROR", e.getMessage());
            return Uni.createFrom().voidItem();
        }

        // Find current active round for the room
        return roundRepository.findLatestByRoomId(roomId)
                .onItem().transformToUni(round -> {
                    if (round == null) {
                        sendError(session, requestId, 4005, "INVALID_STATE", "No active round in room");
                        return Uni.createFrom().voidItem();
                    }

                    if (round.revealedAt != null) {
                        sendError(session, requestId, 4005, "INVALID_STATE", "Round already revealed");
                        return Uni.createFrom().voidItem();
                    }

                    // Find participant by userId and roomId
                    return findParticipantByUserIdAndRoomId(userId, roomId)
                            .onItem().transformToUni(participant -> {
                                if (participant == null) {
                                    sendError(session, requestId, 4003, "FORBIDDEN",
                                            "Participant not found in room");
                                    return Uni.createFrom().voidItem();
                                }

                                // Check if observer (observers cannot vote)
                                if (participant.role == com.scrumpoker.domain.room.RoomRole.OBSERVER) {
                                    sendError(session, requestId, 4003, "FORBIDDEN",
                                            "Observers cannot cast votes");
                                    return Uni.createFrom().voidItem();
                                }

                                // Cast vote via VotingService
                                return castVote(session, requestId, roomId, round.roundId,
                                        participant.participantId, cardValue);
                            });
                })
                .onFailure().recoverWithUni(e -> {
                    Log.errorf(e, "Failed to handle vote.cast.v1: %s", e.getMessage());
                    sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR",
                            "Failed to process vote");
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * Finds a participant by userId and roomId.
     * This is a workaround since RoomParticipantRepository doesn't have this method.
     */
    private Uni<RoomParticipant> findParticipantByUserIdAndRoomId(String userId, String roomId) {
        UUID userUuid = UUID.fromString(userId);

        return participantRepository.find(
                "room.roomId = ?1 and user.userId = ?2",
                roomId,
                userUuid
        ).firstResult();
    }

    /**
     * Casts a vote by calling VotingService.
     */
    private Uni<Void> castVote(Session session, String requestId, String roomId,
                                UUID roundId, UUID participantId, String cardValue) {
        return votingService.castVote(roomId, roundId, participantId, cardValue)
                .onItem().transformToUni(vote -> {
                    Log.infof("Vote cast successfully: participantId=%s, cardValue=%s, roundId=%s",
                            participantId, cardValue, roundId);
                    return Uni.createFrom().voidItem();
                })
                .onFailure(IllegalArgumentException.class).recoverWithUni(e -> {
                    sendError(session, requestId, 4002, "INVALID_VOTE", e.getMessage());
                    return Uni.createFrom().voidItem();
                })
                .onFailure().recoverWithUni(e -> {
                    Log.errorf(e, "Failed to cast vote: %s", e.getMessage());
                    sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR",
                            "Failed to cast vote");
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * Extracts a string value from the payload.
     *
     * @param payload The message payload
     * @param key The key to extract
     * @param required Whether the field is required
     * @return The extracted string value
     * @throws IllegalArgumentException if required field is missing or invalid
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
