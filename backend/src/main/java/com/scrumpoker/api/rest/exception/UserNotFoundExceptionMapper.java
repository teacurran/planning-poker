package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import com.scrumpoker.domain.user.UserNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS exception mapper for UserNotFoundException.
 * Automatically converts domain exceptions to 404 Not Found HTTP responses.
 */
@Provider
public class UserNotFoundExceptionMapper implements ExceptionMapper<UserNotFoundException> {

    @Override
    public Response toResponse(UserNotFoundException exception) {
        ErrorResponse error = new ErrorResponse("USER_NOT_FOUND", exception.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
            .entity(error)
            .build();
    }
}
