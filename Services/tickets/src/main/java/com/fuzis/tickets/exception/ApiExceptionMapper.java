package com.fuzis.tickets.exception;

import com.fuzis.tickets.dto.ErrorResponse;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.OffsetDateTime;

@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {
    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(ApiException exception) {
        return Response.status(exception.getStatus())
                .type("application/json")
                .entity(new ErrorResponse(
                        exception.getCode(),
                        exception.getMessage(),
                        OffsetDateTime.now(),
                        uriInfo.getRequestUri().getPath()))
                .build();
    }
}
