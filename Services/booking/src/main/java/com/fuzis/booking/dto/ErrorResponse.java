package com.fuzis.booking.dto;

public record ErrorResponse(
        String code,
        String message
) {
}
