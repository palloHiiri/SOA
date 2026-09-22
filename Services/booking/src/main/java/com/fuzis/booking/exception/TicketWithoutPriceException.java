package com.fuzis.booking.exception;

public class TicketWithoutPriceException extends RuntimeException {

    public TicketWithoutPriceException(Long ticketId) {
        super("Ticket " + ticketId + " has no price");
    }
}