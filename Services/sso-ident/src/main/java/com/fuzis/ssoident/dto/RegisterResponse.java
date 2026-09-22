package com.fuzis.ssoident.dto;

import java.util.UUID;

public record RegisterResponse(UUID userId, String status) {}
