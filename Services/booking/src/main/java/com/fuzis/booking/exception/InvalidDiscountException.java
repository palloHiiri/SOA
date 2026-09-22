package com.fuzis.booking.exception;

public class InvalidDiscountException extends RuntimeException {

    public InvalidDiscountException(Integer discount) {
        super("Discount must be between 1 and 100, actual: " + discount);
    }
}