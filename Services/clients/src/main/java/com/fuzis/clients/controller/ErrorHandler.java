package com.fuzis.clients.controller;

import com.fuzis.clients.dto.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse badRequest(IllegalArgumentException e) { return new ErrorResponse("BAD_REQUEST", message(e)); }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound(IllegalStateException e) { return new ErrorResponse("NOT_FOUND", message(e)); }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse dataIntegrity(DataIntegrityViolationException e) {
        return new ErrorResponse("BAD_REQUEST", "Invalid client data");
    }

    private static String message(RuntimeException e) { return e.getMessage() == null ? "Request failed" : e.getMessage(); }
}
