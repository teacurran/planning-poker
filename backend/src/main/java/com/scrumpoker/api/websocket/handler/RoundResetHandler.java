package com.scrumpoker.api.websocket.handler;

import com.scrumpoker.api.websocket.ConnectionRegistry;
import com.scrumpoker.api.websocket.WebSocketMessage;
import com.scrumpoker.domain.room.RoomRole;
import com.scrumpoker.domain.room.VotingService;
import com.scrumpoker.repository.RoomParticipantRepository;
import com.scrumpoker.repository.RoundRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles round.reset.v1 messages (host-only).
 * <p>
 * Processes round reset requests from the host, validates authorization,
 * and delegates to VotingService.
 * </p>
 */
@ApplicationScoped
public class RoundResetHandler implements MessageHandler {

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
        return "round.reset.v1";
    }

    @Override
    @io.quarkus.hibernate.reactive.panache.common.WithTransaction
    public Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId) {
        String requestId = message.getRequestId();

        // Verify host role and reset round (transaction provided by @WithTransaction)
        return verifyHostRole(userId, roomId)
                .onItem().transformToUni(isHost -> {
                    if (!isHost) {
                        sendError(session, requestId, 4003, "FORBIDDEN",
                                "Only HOST role can reset rounds");
                        return Uni.createFrom().voidItem();
                    }

                    // Find current active round
                    return roundRepository.findLatestByRoomId(roomId)
                            .onItem().transformToUni(round -> {
                                if (round == null) {
                                    sendError(session, requestId, 4005, "INVALID_STATE",
                                            "No active round in room");
                                    return Uni.createFrom().voidItem();
                                }

                                // Reset round via VotingService
                                return votingService.resetRound(roomId, round.roundId)
                                        .onItem().transformToUni(resetRound -> {
                                            Log.infof("Round reset successfully: roundId=%s, roomId=%s",
                                                    round.roundId, roomId);
                                            return Uni.createFrom().voidItem();
                                        })
                                        .onFailure(IllegalArgumentException.class).recoverWithUni(e -> {
                                            sendError(session, requestId, 4005, "INVALID_STATE",
                                                    e.getMessage());
                                            return Uni.createFrom().voidItem();
                                        })
                                        .onFailure().recoverWithUni(e -> {
                                            Log.errorf(e, "Failed to reset round: %s", e.getMessage());
                                            sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR",
                                                    "Failed to reset round");
                                            return Uni.createFrom().voidItem();
                                        });
                            });
                })
                .onFailure().recoverWithUni(e -> {
                    Log.errorf(e, "Failed to handle round.reset.v1: %s", e.getMessage());
                    sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR",
                            "Failed to process round reset");
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
     * Sends an error message to the client.
     */
    private void sendError(Session session, String requestId, int code, String error, String message) {
        WebSocketMessage errorMessage = WebSocketMessage.createError(
                requestId, code, error, message, Instant.now().toString()
        );
        connectionRegistry.sendToSession(session, errorMessage);
    }
}
