package com.fuzis.clients.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ClientProfileResponse(
UUID clientId,
UUID userId,
String email,
String firstName,
String lastName,
String username,
String phoneNumber,
JsonNode extraFields,
OffsetDateTime updatedAt
) {
}
