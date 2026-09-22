package com.fuzis.ssoident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddGroupRequest(
@NotBlank
@Size(max = 128)
String group
) {
}
