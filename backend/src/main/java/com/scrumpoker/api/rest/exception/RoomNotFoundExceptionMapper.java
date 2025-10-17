package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import com.scrumpoker.domain.room.RoomNotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * JAX-RS exception mapper for RoomNotFoundException.
 * Automatically converts domain exceptions to 404 Not Found HTTP responses.
 */
@Provider
public class RoomNotFoundExceptionMapper implements ExceptionMapper<RoomNotFoundException> {

    @Override
    public Response toResponse(RoomNotFoundException exception) {
        ErrorResponse error = new ErrorResponse("NOT_FOUND", exception.getMessage());
        return Response.status(Response.Status.NOT_FOUND)
            .entity(error)
            .build();
    }
}
