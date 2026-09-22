package com.fuzis.booking.exception;

public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException() {
        super("Authentication required or session is invalid");
    }
}