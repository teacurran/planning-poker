package com.scrumpoker.api.rest.exception;

import com.scrumpoker.api.rest.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JAX-RS exception mapper for Bean Validation errors.
 * Converts ConstraintViolationException to 400 Bad Request with field-level error details.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Map<String, Object> details = new HashMap<>();

        // Extract field-level validation errors
        Map<String, String> fieldErrors = exception.getConstraintViolations()
            .stream()
            .collect(Collectors.toMap(
                violation -> getFieldName(violation),
                ConstraintViolation::getMessage,
                (msg1, msg2) -> msg1 + "; " + msg2 // Combine multiple errors for same field
            ));

        details.put("fieldErrors", fieldErrors);

        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR",
            "Request validation failed",
            details
        );

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(error)
            .build();
    }

    /**
     * Extracts the field name from a constraint violation path.
     */
    private String getFieldName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        String[] parts = path.split("\\.");
        return parts[parts.length - 1];
    }
}
