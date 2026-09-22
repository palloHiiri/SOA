package com.fuzis.ssoident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record PasswordResetConfirmRequest(
        UUID code,
        @NotBlank @Size(min = 8, max = 1024) String newPassword
) {}
