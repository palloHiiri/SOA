package com.fuzis.tickets.exception;

public class ApiException extends RuntimeException {
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "BAD_REQUEST", message);
    }
    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }
    public static ApiException conflict(String message) {
        return new ApiException(409, "CONFLICT", message);
    }
    public static ApiException internal(String message) {
        return new ApiException(500, "INTERNAL_ERROR", message);
    }
}
