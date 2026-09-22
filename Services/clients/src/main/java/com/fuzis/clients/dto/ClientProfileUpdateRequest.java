package com.fuzis.clients.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ClientProfileUpdateRequest(
@Size(max = 64) String phoneNumber,
@NotNull JsonNode extraFields
) {
}
