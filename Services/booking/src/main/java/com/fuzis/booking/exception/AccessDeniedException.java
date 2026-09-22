package com.fuzis.booking.exception;

public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException() {
        super("Access to passenger is forbidden");
    }
}