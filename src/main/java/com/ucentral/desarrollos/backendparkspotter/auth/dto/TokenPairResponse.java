package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import java.util.UUID;

public record TokenPairResponse(
        UUID userId,
        String email,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
}
