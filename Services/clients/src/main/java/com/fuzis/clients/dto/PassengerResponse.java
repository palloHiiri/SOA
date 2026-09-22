package com.fuzis.clients.dto;

import java.time.LocalDate;
import java.util.UUID;

public record PassengerResponse(
UUID id,
UUID clientId,
String firstName,
String middleName,
String lastName,
Integer documentTypeId,
String documentTypeCode,
String documentTypeName,
String documentSeriesNumber,
String documentNumber,
LocalDate birthDate,
String email,
String phoneNumber
) {
}
