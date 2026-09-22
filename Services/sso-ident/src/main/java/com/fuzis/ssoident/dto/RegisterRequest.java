package com.fuzis.ssoident.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
@NotBlank
@Email
@Size(max = 320)
String email,
@Size(min = 3, max = 128) String username,
@NotBlank
@Size(min = 8, max = 1024)
String password,
@Size(max = 255) String firstName,
@Size(max = 255) String lastName
) {
}
