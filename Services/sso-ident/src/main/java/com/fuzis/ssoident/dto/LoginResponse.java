package com.fuzis.ssoident.dto;

import java.util.UUID;

public record LoginResponse(String status, UUID userId, UUID challengeId) {
    public static LoginResponse authenticated(UUID userId) {
        return new LoginResponse("AUTHENTICATED", userId, null);
    }
    public static LoginResponse mfaRequired(UUID userId, UUID challengeId) {
        return new LoginResponse("MFA_REQUIRED", userId, challengeId);
    }
}
