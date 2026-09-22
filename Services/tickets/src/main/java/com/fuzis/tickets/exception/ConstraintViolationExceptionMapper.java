package com.fuzis.tickets.exception;

import com.fuzis.tickets.dto.ErrorResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {
    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        String message = exception.getConstraintViolations().stream()
        .map(ConstraintViolation::getMessage)
        .distinct()
        .sorted()
        .collect(Collectors.joining("; "));
        return Response.status(Response.Status.BAD_REQUEST)
        .type("application/json")
        .entity(new ErrorResponse(
        "VALIDATION_ERROR",
        message.isBlank() ? "Request validation failed" : message,
        OffsetDateTime.now(),
        uriInfo.getRequestUri().getPath()))
        .build();
    }
}
