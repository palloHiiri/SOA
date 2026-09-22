package com.fuzis.booking.exception;

import com.fuzis.booking.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTicketNotFoundException(TicketNotFoundException ex) {
        ErrorResponse errorResponse = new ErrorResponse("TICKET_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }

    @ExceptionHandler(TicketAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleTicketAlreadyBookedException(TicketAlreadyBookedException ex) {
        ErrorResponse errorResponse = new ErrorResponse("TICKET_ALREADY_BOOKED", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse);
    }
    @ExceptionHandler(NoAvailableSeatException.class)
    public ResponseEntity<ErrorResponse> handleNoAvailableSeatException(NoAvailableSeatException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).
                body(new ErrorResponse("NO_AVAILABLE_SEAT", exception.getMessage()));
    }

    @ExceptionHandler(InvalidDiscountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDiscountException(InvalidDiscountException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_DISCOUNT", exception.getMessage()));
    }
}
