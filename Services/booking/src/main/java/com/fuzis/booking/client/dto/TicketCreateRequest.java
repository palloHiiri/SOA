package com.fuzis.booking.client.dto;

import java.math.BigDecimal;

public record TicketCreateRequest(
        String name,
        Long venueId,
        String carriageNumber,
        String seatNumber,
        BigDecimal basePrice,
        Integer discount,
        Boolean refundable,
        String type
) {
}