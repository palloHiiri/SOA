package com.fuzis.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketResponse (
        Long id,
        String name,
        BigDecimal basePrice,
        Integer discount
){
}
