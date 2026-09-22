package com.fuzis.tickets.exception;

import com.fuzis.tickets.dto.ErrorResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.OffsetDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        LOG.log(Level.SEVERE, "Unhandled tickets service exception", exception);
        return Response.serverError()
        .type("application/json")
        .entity(new ErrorResponse(
        "INTERNAL_ERROR",
        "Internal server error",
        OffsetDateTime.now(),
        uriInfo.getRequestUri().getPath()))
        .build();
    }
}
