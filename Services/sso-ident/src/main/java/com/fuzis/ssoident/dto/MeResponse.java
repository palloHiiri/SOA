package com.fuzis.ssoident.dto;

import java.util.Map;
import java.util.UUID;

public record MeResponse(
UUID userId,
String subject,
String status,
Map<String, String> attributes
) {
}
