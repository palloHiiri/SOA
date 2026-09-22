package com.fuzis.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VenueResponse(
        Long id,
        String name,
        Integer trainSetId
) {
}