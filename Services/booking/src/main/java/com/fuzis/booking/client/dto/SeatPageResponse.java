package com.fuzis.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeatPageResponse(
        List<SeatResponse> content
) {
}