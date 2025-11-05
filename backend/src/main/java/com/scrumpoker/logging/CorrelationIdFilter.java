package com.scrumpoker.logging;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * JAX-RS filter that adds correlation IDs to all HTTP requests and logs request/response cycles.
 * <p>
 * This filter implements both {@link ContainerRequestFilter} and {@link ContainerResponseFilter}
 * to handle the complete request-response lifecycle. It runs early in the request processing
 * pipeline (before authentication) to ensure correlation IDs are available to all subsequent
 * filters, controllers, and services.
 * </p>
 *
 * <h2>Correlation ID Generation and Propagation</h2>
 * <p>
 * The filter implements distributed tracing by:
 * <ul>
 *   <li>Extracting correlation ID from incoming {@code X-Correlation-ID} header if present</li>
 *   <li>Generating a new UUID-based correlation ID if not provided</li>
 *   <li>Adding the correlation ID to MDC (Mapped Diagnostic Context) for automatic inclusion in all log entries</li>
 *   <li>Including the correlation ID in response headers for client-side tracing</li>
 *   <li>Cleaning up MDC after response to prevent thread-local leakage</li>
 * </ul>
 * </p>
 *
 * <h2>Request Logging</h2>
 * <p>
 * The filter logs every HTTP request with the following information:
 * <ul>
 *   <li>HTTP method (GET, POST, PUT, DELETE, etc.)</li>
 *   <li>Request path (e.g., /api/v1/rooms/123)</li>
 *   <li>Response status code (200, 404, 500, etc.)</li>
 *   <li>Request duration in milliseconds</li>
 *   <li>Correlation ID (automatically included via MDC)</li>
 * </ul>
 * </p>
 *
 * <h2>Integration with Structured Logging</h2>
 * <p>
 * When JSON logging is enabled ({@code quarkus.log.console.json=true}), the correlation ID
 * and other MDC fields are automatically serialized into the JSON log output. This enables
 * efficient log aggregation and querying in systems like Loki, CloudWatch Logs, or Elasticsearch.
 * </p>
 *
 * <h2>Thread Safety and Reactive Compatibility</h2>
 * <p>
 * MDC is thread-local, which is safe for traditional servlet-based request handling.
 * For Quarkus reactive programming with Mutiny (Uni/Multi), the Quarkus Context Propagation
 * mechanism automatically propagates MDC values across reactive chain operators.
 * </p>
 *
 * @see LoggingConstants
 * @see org.jboss.logging.MDC
 * @since 1.0.0
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(CorrelationIdFilter.class);

    /**
     * Processes incoming HTTP requests before they reach the application logic.
     * <p>
     * This method:
     * <ol>
     *   <li>Extracts or generates a correlation ID</li>
     *   <li>Adds the correlation ID to MDC for log enrichment</li>
     *   <li>Stores the request start timestamp for duration calculation</li>
     *   <li>Adds the correlation ID to the response headers</li>
     * </ol>
     * </p>
     *
     * @param requestContext the JAX-RS request context
     * @throws IOException if an I/O error occurs during request processing
     */
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // 1. Get or generate correlation ID
        String correlationId = requestContext.getHeaderString(LoggingConstants.CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            LOG.tracef("Generated new correlation ID: %s", correlationId);
        } else {
            LOG.tracef("Using existing correlation ID from header: %s", correlationId);
        }

        // 2. Add to MDC for automatic inclusion in all log entries
        MDC.put(LoggingConstants.CORRELATION_ID, correlationId);

        // 3. Store start time for duration calculation in response filter
        requestContext.setProperty(LoggingConstants.REQUEST_START_TIME, System.currentTimeMillis());

        // 4. Store correlation ID in request context for response filter
        requestContext.setProperty(LoggingConstants.CORRELATION_ID, correlationId);

        LOG.debugf("Incoming request: %s %s", requestContext.getMethod(), requestContext.getUriInfo().getPath());
    }

    /**
     * Processes HTTP responses before they are sent back to the client.
     * <p>
     * This method:
     * <ol>
     *   <li>Calculates the request processing duration</li>
     *   <li>Logs the complete request-response cycle (method, path, status, duration)</li>
     *   <li>Adds the correlation ID to response headers for client-side tracing</li>
     *   <li>Cleans up MDC to prevent thread-local leakage</li>
     * </ol>
     * </p>
     * <p>
     * The MDC cleanup is performed in a {@code finally} block to ensure it happens even if
     * logging or response modification fails.
     * </p>
     *
     * @param requestContext the JAX-RS request context
     * @param responseContext the JAX-RS response context
     * @throws IOException if an I/O error occurs during response processing
     */
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        try {
            // 1. Calculate request duration
            Long startTime = (Long) requestContext.getProperty(LoggingConstants.REQUEST_START_TIME);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : -1;

            // 2. Get correlation ID from request context
            String correlationId = (String) requestContext.getProperty(LoggingConstants.CORRELATION_ID);

            // 3. Add correlation ID to response headers for client-side tracing
            if (correlationId != null) {
                responseContext.getHeaders().putSingle(LoggingConstants.CORRELATION_ID_HEADER, correlationId);
            }

            // 4. Log the complete request-response cycle
            String method = requestContext.getMethod();
            String path = requestContext.getUriInfo().getPath();
            int status = responseContext.getStatus();

            if (status >= 500) {
                // Log server errors at ERROR level
                LOG.errorf("HTTP %s %s -> %d (%d ms)", method, path, status, duration);
            } else if (status >= 400) {
                // Log client errors at WARN level
                LOG.warnf("HTTP %s %s -> %d (%d ms)", method, path, status, duration);
            } else {
                // Log successful requests at INFO level
                LOG.infof("HTTP %s %s -> %d (%d ms)", method, path, status, duration);
            }

        } catch (Exception e) {
            // Log any errors in the filter itself, but don't disrupt the response
            LOG.error("Error in CorrelationIdFilter response processing", e);
        } finally {
            // 5. Always clean up MDC to prevent thread-local leakage
            // This is critical in thread pool environments where threads are reused
            MDC.clear();
        }
    }
}
