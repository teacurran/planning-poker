package com.scrumpoker.logging;

/**
 * Constants for structured logging and correlation ID tracking.
 * <p>
 * This class defines standard keys used for MDC (Mapped Diagnostic Context) fields,
 * HTTP headers, and request attributes related to logging and distributed tracing.
 * These constants ensure consistent naming across the application's logging infrastructure.
 * </p>
 *
 * @see org.jboss.logging.MDC
 * @since 1.0.0
 */
public final class LoggingConstants {

    /**
     * MDC key for the correlation ID field in log entries.
     * <p>
     * The correlation ID uniquely identifies a request/response cycle or WebSocket session,
     * enabling distributed tracing across multiple services and log aggregation systems.
     * This field appears as "correlationId" in JSON log output.
     * </p>
     */
    public static final String CORRELATION_ID = "correlationId";

    /**
     * HTTP header name for correlation ID propagation.
     * <p>
     * This header is used for distributed tracing:
     * - Incoming requests: If present, the value is used as the correlation ID
     * - Outgoing responses: The correlation ID is added to response headers
     * - Outgoing requests to external services: Should include this header for trace continuity
     * </p>
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Request context property key for storing the request start timestamp.
     * <p>
     * Used internally by {@link CorrelationIdFilter} to calculate request duration.
     * The value is a {@link Long} representing milliseconds since epoch.
     * </p>
     */
    public static final String REQUEST_START_TIME = "request.startTime";

    /**
     * MDC key for user ID field in log entries.
     * <p>
     * When a user is authenticated, their ID should be added to MDC to associate
     * log entries with specific users. This field is omitted for anonymous requests.
     * </p>
     */
    public static final String USER_ID = "userId";

    /**
     * MDC key for room ID field in log entries.
     * <p>
     * When a request or operation is scoped to a specific estimation room,
     * the room ID should be added to MDC to provide business context.
     * </p>
     */
    public static final String ROOM_ID = "roomId";

    /**
     * MDC key for semantic action field in log entries.
     * <p>
     * Describes the business operation being performed (e.g., "vote.cast",
     * "room.created", "subscription.upgraded"). This enables business-level
     * log analysis and monitoring.
     * </p>
     */
    public static final String ACTION = "action";

    /**
     * WebSocket session user property key for storing correlation ID.
     * <p>
     * Since WebSocket connections are long-lived and process multiple messages,
     * the correlation ID is stored in session user properties and added to MDC
     * before processing each message.
     * </p>
     */
    public static final String WS_CORRELATION_ID_PROPERTY = "ws.correlationId";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private LoggingConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
