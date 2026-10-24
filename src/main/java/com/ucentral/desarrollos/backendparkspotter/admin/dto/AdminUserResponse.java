package com.ucentral.desarrollos.backendparkspotter.admin.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        Set<String> roles,
        boolean enabled,
        Instant createdAt
) {
}
