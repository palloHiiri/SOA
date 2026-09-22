package com.fuzis.booking.exception;

public class NoAvailableSeatException extends RuntimeException {

    public NoAvailableSeatException(Long ticketId) {
        super("No available seat found for ticket " + ticketId);
    }
}