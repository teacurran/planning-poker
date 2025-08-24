package com.terrencecurran.planningpoker.websocket.message;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Validates WebSocket messages to prevent common errors.
 * This is a defensive programming measure to catch issues early.
 */
@ApplicationScoped
public class MessageValidator {
    
    /**
     * Validates a JOIN_ROOM message
     */
    public boolean validateJoinRoom(JoinRoomMessage message) {
        return message != null 
            && message.username != null 
            && !message.username.trim().isEmpty()
            && message.username.length() <= 50; // Reasonable username length
    }
    
    /**
     * Validates a VOTE message
     */
    public boolean validateVote(VoteMessage message) {
        return message != null 
            && message.value != null 
            && !message.value.trim().isEmpty()
            && message.value.length() <= 10; // Reasonable vote value length
    }
    
    /**
     * Validates any message type
     */
    public String validateMessage(BaseMessage message) {
        if (message == null) {
            return "Message is null";
        }
        
        if (message.type == null) {
            return "Message type is null";
        }
        
        // Type-specific validation
        if (message instanceof JoinRoomMessage) {
            JoinRoomMessage joinMsg = (JoinRoomMessage) message;
            if (!validateJoinRoom(joinMsg)) {
                return "Invalid JOIN_ROOM message: username is required and must be <= 50 characters";
            }
        } else if (message instanceof VoteMessage) {
            VoteMessage voteMsg = (VoteMessage) message;
            if (!validateVote(voteMsg)) {
                return "Invalid VOTE message: value is required and must be <= 10 characters";
            }
        }
        
        return null; // Valid
    }
}