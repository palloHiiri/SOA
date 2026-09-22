package com.fuzis.clients.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PassengerCreateRequest(
@NotBlank
@Size(max = 255)
String firstName,
@NotNull
@Size(max = 255)
String middleName,
@NotBlank
@Size(max = 255)
String lastName,
@NotNull Integer documentTypeId,
@NotBlank
@Size(max = 64)
String documentSeriesNumber,
@NotBlank
@Size(max = 128)
String documentNumber,
@NotNull
@Past
LocalDate birthDate,
@NotBlank
@Email
@Size(max = 320)
String email,
@Size(max = 64) String phoneNumber
) {
}
