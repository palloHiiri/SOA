package com.fuzis.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PassengerResponse(
        UUID id,
        UUID clientId,
        String firstName,
        String lastName
) {
}
