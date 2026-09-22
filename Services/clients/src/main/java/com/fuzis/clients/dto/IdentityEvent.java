package com.fuzis.clients.dto;

import java.time.Instant;
import java.util.UUID;

public record IdentityEvent(
        String event,
        UUID userId,
        String username,
        String email,
        String firstName,
        String lastName,
        Long eventVersion,
        Instant updatedAt
) {}
