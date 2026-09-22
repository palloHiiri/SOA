package com.fuzis.booking.exception;

import java.util.UUID;

public class PassengerNotFoundException extends RuntimeException {

    public PassengerNotFoundException(UUID passengerId) {
        super("Passenger " + passengerId + " not found");
    }
}