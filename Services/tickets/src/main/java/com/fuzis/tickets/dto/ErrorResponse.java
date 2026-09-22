package com.fuzis.tickets.dto;

import java.time.OffsetDateTime;

public class ErrorResponse {
    private String code;
    private String message;
    private OffsetDateTime timestamp;
    private String path;
    public ErrorResponse() {
    }
    public ErrorResponse(String code, String message, OffsetDateTime timestamp, String path) {
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
        this.path = path;
    }
    public String getCode() {
        return code;
    }
    public void setCode(String code) {
        this.code = code;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public OffsetDateTime getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }
    public String getPath() {
        return path;
    }
    public void setPath(String path) {
        this.path = path;
    }
}
