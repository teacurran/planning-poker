package com.scrumpoker.security;

/**
 * Custom exception that represents JWT processing failures.
 * <p>
 * The exception captures the failure reason so callers can react accordingly
 * (e.g., differentiate between expired tokens vs invalid signatures).
 * </p>
 */
public class JwtException extends RuntimeException {

    /**
     * Categorizes the type of JWT failure for better error handling.
     */
    public enum Reason {
        INVALID_ACCESS_TOKEN,
        EXPIRED_ACCESS_TOKEN,
        INVALID_REFRESH_TOKEN,
        USER_NOT_FOUND,
        TOKEN_STORAGE_FAILURE
    }

    private final Reason reason;

    /**
     * Creates a new JwtException with a message and reason.
     *
     * @param message Human-readable description of the failure
     * @param reason  Structured reason used for programmatic handling
     */
    public JwtException(String message, Reason reason) {
        super(message);
        this.reason = reason;
    }

    /**
     * Creates a new JwtException with a message, cause, and reason.
     *
     * @param message Human-readable description of the failure
     * @param cause   Underlying cause of the exception
     * @param reason  Structured reason used for programmatic handling
     */
    public JwtException(String message, Throwable cause, Reason reason) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * Returns the structured reason for the failure.
     *
     * @return Reason enum explaining why the exception was thrown
     */
    public Reason reason() {
        return reason;
    }
}
