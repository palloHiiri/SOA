package com.fuzis.booking.exception;

public class TicketCreationConflictException extends RuntimeException {
    public TicketCreationConflictException(Long sourceTicketId) {
        super(
                "Concurrent ticket creation for source ticket "
                        + sourceTicketId
                        + "; retry the request");
    }
}
