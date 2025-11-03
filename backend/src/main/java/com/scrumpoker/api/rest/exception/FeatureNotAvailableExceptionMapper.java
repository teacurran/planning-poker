package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import com.scrumpoker.security.FeatureNotAvailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS exception mapper for FeatureNotAvailableException.
 * Automatically converts tier enforcement exceptions to 403 Forbidden HTTP responses
 * with user-friendly upgrade prompts.
 * <p>
 * This mapper is automatically registered via the {@code @Provider} annotation and
 * will be invoked whenever a {@link FeatureNotAvailableException} is thrown from
 * any REST endpoint or service method.
 * </p>
 * <p>
 * <strong>Response Format:</strong>
 * <pre>
 * HTTP/1.1 403 Forbidden
 * Content-Type: application/json
 *
 * {
 *   "error": "FEATURE_NOT_AVAILABLE",
 *   "message": "This feature requires Pro tier or higher. Your current tier is Free. Upgrade your subscription to access Advanced Reports.",
 *   "timestamp": "2025-01-15T10:30:00Z"
 * }
 * </pre>
 * </p>
 *
 * @see FeatureNotAvailableException
 * @see com.scrumpoker.security.FeatureGate
 */
@Provider
public class FeatureNotAvailableExceptionMapper implements ExceptionMapper<FeatureNotAvailableException> {

    /**
     * Converts a FeatureNotAvailableException to a 403 Forbidden HTTP response.
     * <p>
     * The response includes a user-friendly error message with upgrade call-to-action,
     * guiding users to purchase a higher subscription tier to access the requested feature.
     * </p>
     *
     * @param exception The feature not available exception containing tier and feature context
     * @return HTTP 403 response with ErrorResponse JSON body
     */
    @Override
    public Response toResponse(FeatureNotAvailableException exception) {
        ErrorResponse error = new ErrorResponse("FEATURE_NOT_AVAILABLE", exception.getMessage());
        return Response.status(Response.Status.FORBIDDEN)
            .entity(error)
            .build();
    }
}
