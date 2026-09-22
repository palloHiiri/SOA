package com.fuzis.ssoident.dto;

import java.time.Instant;
import java.util.UUID;

public record VerificationResponse(UUID challengeId, Instant expiresAt) {}
