package com.fuzis.tickets.exception;

import com.fuzis.tickets.dto.ErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.OffsetDateTime;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {
    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        int status = exception.getResponse() == null ? 400 : exception.getResponse().getStatus();
        String code = status == 404 ? "NOT_FOUND" : "BAD_REQUEST";
        String message = exception.getMessage() == null ? "Invalid HTTP request" : exception.getMessage();
        return Response.status(status)
                .type("application/json")
                .entity(new ErrorResponse(code, message, OffsetDateTime.now(), uriInfo.getRequestUri().getPath()))
                .build();
    }
}
