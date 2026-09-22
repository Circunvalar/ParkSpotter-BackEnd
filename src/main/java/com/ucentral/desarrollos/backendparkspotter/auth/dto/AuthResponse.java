package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import java.util.UUID;

public record AuthResponse(
        UUID userId,
        String email,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
}
