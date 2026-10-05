package com.fuzis.booking.exception;

import com.fuzis.booking.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTicketNotFoundException(TicketNotFoundException ex) {
        log.warn("Ticket not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("TICKET_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(TicketAlreadyBookedException.class)
    public ResponseEntity<ErrorResponse> handleTicketAlreadyBookedException(TicketAlreadyBookedException ex) {
        log.warn("Ticket already booked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("TICKET_ALREADY_BOOKED", ex.getMessage()));
    }

    @ExceptionHandler(NoAvailableSeatException.class)
    public ResponseEntity<ErrorResponse> handleNoAvailableSeatException(NoAvailableSeatException exception) {
        log.warn("No available seat: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("NO_AVAILABLE_SEAT", exception.getMessage()));
    }

    @ExceptionHandler(InvalidDiscountException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDiscountException(InvalidDiscountException exception) {
        log.warn("Invalid discount: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("INVALID_DISCOUNT", exception.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException exception) {
        log.warn("Unauthorized request: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResponse("UNAUTHORIZED", exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException exception) {
        log.warn("Access denied: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResponse("ACCESS_DENIED", exception.getMessage()));
    }

    @ExceptionHandler(PassengerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePassengerNotFoundException(PassengerNotFoundException exception) {
        log.warn("Passenger not found: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PASSENGER_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(TicketWithoutPriceException.class)
    public ResponseEntity<ErrorResponse> handleTicketWithoutPriceException(TicketWithoutPriceException exception) {
        log.warn("Ticket has no price: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("TICKET_WITHOUT_PRICE", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception exception) {
        log.error("Unhandled booking service exception: {}", exception.toString(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("INTERNAL_SERVER_ERROR", "Internal server error"));
    }
}
