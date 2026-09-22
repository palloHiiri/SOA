package com.fuzis.booking.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record BookResponse(
        Long ticketId,
        UUID passengerId,
        BigDecimal price
) {

}
