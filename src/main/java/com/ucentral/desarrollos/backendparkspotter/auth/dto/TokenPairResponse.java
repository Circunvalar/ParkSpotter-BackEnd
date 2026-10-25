package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * @param roles roles de la cuenta (ROLE_DRIVER, ROLE_OWNER, ROLE_ADMIN...) para que el front
 *              decida qué pantallas mostrar sin decodificar el JWT
 */
public record TokenPairResponse(
        UUID userId,
        String email,
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        List<String> roles
) {
}
