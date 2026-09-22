package com.fuzis.ssoident.controller;

import com.fuzis.ssoident.service.ConflictException;
import com.fuzis.ssoident.service.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> badRequest(IllegalArgumentException e) {
        return Map.of("code", "BAD_REQUEST", "message", e.getMessage() == null ? "Bad request" : e.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> notFound(NotFoundException e) {
        return Map.of("code", "NOT_FOUND", "message", e.getMessage() == null ? "Not found" : e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> conflict(ConflictException e) {
        return Map.of("code", "CONFLICT", "message", e.getMessage() == null ? "Conflict" : e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, Object> unauthorized(IllegalStateException e) {
        return Map.of("code", "UNAUTHORIZED", "message", e.getMessage() == null ? "Unauthorized" : e.getMessage());
    }
}
