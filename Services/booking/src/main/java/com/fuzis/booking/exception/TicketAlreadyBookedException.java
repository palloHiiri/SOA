package com.fuzis.booking.exception;

public class TicketAlreadyBookedException extends RuntimeException {

    public TicketAlreadyBookedException(Long ticketId) {
        super("Ticket " + ticketId + " is already booked");
    }
}