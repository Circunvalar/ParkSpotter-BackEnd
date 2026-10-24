package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Set<String> roles,
        boolean enabled,
        Instant createdAt
) {
}
