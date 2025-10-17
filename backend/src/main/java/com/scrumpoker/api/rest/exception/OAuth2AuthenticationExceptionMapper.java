package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import com.scrumpoker.integration.oauth.OAuth2AuthenticationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/**
 * JAX-RS exception mapper for OAuth2AuthenticationException.
 * Converts OAuth2 authentication failures to 401 Unauthorized HTTP responses.
 * Logs security events but returns generic error messages to prevent
 * information leakage to potential attackers.
 */
@Provider
public class OAuth2AuthenticationExceptionMapper
        implements ExceptionMapper<OAuth2AuthenticationException> {

    private static final Logger LOG =
            Logger.getLogger(OAuth2AuthenticationExceptionMapper.class);

    @Override
    public Response toResponse(final OAuth2AuthenticationException exception) {
        // Log the full error details for debugging
        // (but don't leak provider-specific details to client)
        LOG.errorf(exception, "OAuth2 authentication failed: %s",
                exception.getMessage());

        // Return generic error message to client
        // (prevents information disclosure)
        ErrorResponse error = new ErrorResponse(
                "OAUTH_AUTHENTICATION_FAILED",
                "Authentication failed. Please try again.");

        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(error)
                .build();
    }
}
