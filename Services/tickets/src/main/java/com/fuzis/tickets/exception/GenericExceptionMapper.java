package com.fuzis.tickets.exception;

import com.fuzis.tickets.dto.ErrorResponse;

import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Context private UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable cause = exception;
                cause != null && visited.add(cause);
                cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String state = sql.getSQLState();
                if ("40001".equals(state) || "40P01".equals(state)) {
                    return error(409, "CONFLICT", "Concurrent modification; retry the request");
                }
                if ("23505".equals(state)) {
                    return error(409, "CONFLICT", "The requested unique value is already in use");
                }
                if ("23503".equals(state)) {
                    return error(409, "CONFLICT", "The resource is referenced or no longer exists");
                }
                if ("23514".equals(state) || "22003".equals(state)) {
                    return error(400, "BAD_REQUEST", "A field violates a database constraint");
                }
            }
            if (cause instanceof PessimisticLockException
                    || cause instanceof OptimisticLockException
                    || cause instanceof LockTimeoutException) {
                return error(409, "CONFLICT", "Concurrent modification; retry the request");
            }
        }
        LOG.log(Level.SEVERE, "Unhandled tickets service exception", exception);
        return error(500, "INTERNAL_ERROR", "Internal server error");
    }

    private Response error(int status, String code, String message) {
        return Response.status(status)
                .type("application/json")
                .entity(
                        new ErrorResponse(
                                code,
                                message,
                                OffsetDateTime.now(),
                                uriInfo.getRequestUri().getPath()))
                .build();
    }
}
