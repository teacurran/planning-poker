package com.scrumpoker.api.websocket.handler;

import com.scrumpoker.api.websocket.ConnectionRegistry;
import com.scrumpoker.api.websocket.WebSocketMessage;
import com.scrumpoker.repository.RoomParticipantRepository;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.Session;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles chat.message.v1 messages.
 * <p>
 * Processes chat messages from participants and broadcasts them to all
 * participants in the room.
 * </p>
 * <p>
 * Note: This implementation does NOT persist chat messages to the database.
 * Chat persistence is out of scope for this task. Messages are only broadcasted
 * in real-time via ConnectionRegistry.
 * </p>
 */
@ApplicationScoped
public class ChatMessageHandler implements MessageHandler {

    @Inject
    RoomParticipantRepository participantRepository;

    @Inject
    ConnectionRegistry connectionRegistry;

    @Override
    public String getMessageType() {
        return "chat.message.v1";
    }

    @Override
    public Uni<Void> handle(Session session, WebSocketMessage message, String userId, String roomId) {
        String requestId = message.getRequestId();
        Map<String, Object> payload = message.getPayload();

        // Extract and validate message content
        String messageContent;
        String replyToMessageId = null;

        try {
            messageContent = extractString(payload, "message", true);

            // Validate message length (1-2000 characters per protocol spec)
            if (messageContent.length() > 2000) {
                sendError(session, requestId, 4004, "VALIDATION_ERROR",
                        "Message cannot exceed 2000 characters");
                return Uni.createFrom().voidItem();
            }

            replyToMessageId = extractString(payload, "replyToMessageId", false);

        } catch (IllegalArgumentException e) {
            sendError(session, requestId, 4004, "VALIDATION_ERROR", e.getMessage());
            return Uni.createFrom().voidItem();
        }

        final String finalMessageContent = messageContent;
        final String finalReplyToMessageId = replyToMessageId;

        // Find participant to get display name
        return findParticipantByUserIdAndRoomId(userId, roomId)
                .onItem().transformToUni(participant -> {
                    if (participant == null) {
                        sendError(session, requestId, 4003, "FORBIDDEN",
                                "Participant not found in room");
                        return Uni.createFrom().voidItem();
                    }

                    // Create chat message payload for broadcast
                    Map<String, Object> chatPayload = new HashMap<>();
                    chatPayload.put("messageId", UUID.randomUUID().toString());
                    chatPayload.put("participantId", participant.participantId.toString());
                    chatPayload.put("displayName", participant.displayName);
                    chatPayload.put("message", finalMessageContent);
                    chatPayload.put("sentAt", Instant.now().toString());
                    chatPayload.put("replyToMessageId", finalReplyToMessageId);

                    // Broadcast chat message to all participants in room
                    WebSocketMessage chatBroadcast = new WebSocketMessage(
                            "chat.message.v1",
                            requestId,
                            chatPayload
                    );

                    connectionRegistry.broadcastToRoom(roomId, chatBroadcast);

                    Log.infof("Chat message broadcast to room %s: participantId=%s, message=%s",
                            roomId, participant.participantId, finalMessageContent);

                    return Uni.createFrom().voidItem();
                })
                .onFailure().recoverWithUni(e -> {
                    Log.errorf(e, "Failed to handle chat.message.v1: %s", e.getMessage());
                    sendError(session, requestId, 4999, "INTERNAL_SERVER_ERROR",
                            "Failed to process chat message");
                    return Uni.createFrom().voidItem();
                });
    }

    /**
     * Finds a participant by userId and roomId.
     */
    private Uni<com.scrumpoker.domain.room.RoomParticipant> findParticipantByUserIdAndRoomId(
            String userId, String roomId) {
        UUID userUuid = UUID.fromString(userId);

        return participantRepository.find(
                "room.roomId = ?1 and user.userId = ?2",
                roomId,
                userUuid
        ).firstResult();
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
