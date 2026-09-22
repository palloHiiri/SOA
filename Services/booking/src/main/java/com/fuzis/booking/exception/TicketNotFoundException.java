package com.fuzis.booking.exception;

public class TicketNotFoundException extends RuntimeException{
    public TicketNotFoundException(Long ticketId) {
        super("Ticket with ID " + ticketId + " not found.");
    }
}
