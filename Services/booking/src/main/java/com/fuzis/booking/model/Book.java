package com.fuzis.booking.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Book(
        Long id,
        Long ticketId,
        UUID passengerId,
        BigDecimal price,
        Long sourceTicketId,
        OffsetDateTime bookDate
) {
}