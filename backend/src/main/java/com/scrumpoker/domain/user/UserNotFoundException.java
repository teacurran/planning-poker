package com.scrumpoker.domain.user;

import java.util.UUID;

/**
 * Exception thrown when a requested user is not found or has been soft-deleted.
 */
public class UserNotFoundException extends RuntimeException {
    private final UUID userId;

    public UserNotFoundException(UUID userId) {
        super("User not found: " + userId);
        this.userId = userId;
    }

    public UserNotFoundException(UUID userId, Throwable cause) {
        super("User not found: " + userId, cause);
        this.userId = userId;
    }

    public UUID getUserId() {
        return userId;
    }
}
