package com.fuzis.tickets.exception;

import com.fuzis.tickets.dto.ErrorResponse;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.OffsetDateTime;

@Provider
public class JsonbExceptionMapper implements ExceptionMapper<JsonbException> {
    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(JsonbException exception) {
        return Response.status(Response.Status.BAD_REQUEST)
                .type("application/json")
                .entity(new ErrorResponse("BAD_REQUEST", "Invalid JSON request body", OffsetDateTime.now(), uriInfo.getRequestUri().getPath()))
                .build();
    }
}
