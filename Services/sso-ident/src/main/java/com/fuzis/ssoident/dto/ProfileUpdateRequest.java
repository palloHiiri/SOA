package com.fuzis.ssoident.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
@Email
@Size(max = 320)
String email,
@Size(min = 3, max = 128) String username,
@Size(max = 255) String firstName,
@Size(max = 255) String lastName
) {
    public boolean isEmpty() {
        return email == null && username == null && firstName == null && lastName == null;
    }
}
