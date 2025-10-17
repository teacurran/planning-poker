package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS exception mapper for IllegalArgumentException.
 * Converts domain validation errors to 400 Bad Request HTTP responses.
 */
@Provider
public class IllegalArgumentExceptionMapper implements ExceptionMapper<IllegalArgumentException> {

    @Override
    public Response toResponse(IllegalArgumentException exception) {
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", exception.getMessage());
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(error)
            .build();
    }
}
