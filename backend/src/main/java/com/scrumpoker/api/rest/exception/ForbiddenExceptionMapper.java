package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Converts {@link ForbiddenException} instances into HTTP 403 responses
 * with the standardized {@link ErrorResponse} payload defined in OpenAPI.
 */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(ForbiddenException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = "Access is denied";
        }
        ErrorResponse error = new ErrorResponse("FORBIDDEN", message);
        return Response.status(Response.Status.FORBIDDEN)
            .entity(error)
            .build();
    }
}
