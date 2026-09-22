package com.fuzis.ssoident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record MfaCodeVerifyRequest(
UUID challengeId,
@NotBlank
@Pattern(regexp = "^[0-9]{6}$")
String code
) {
}
