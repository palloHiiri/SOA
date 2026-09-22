package com.fuzis.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeatResponse(
        Integer id,
        String seatNumber
) {
}