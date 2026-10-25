package com.ucentral.desarrollos.backendparkspotter.review.dto;

import java.time.Instant;
import java.util.UUID;

/** @param authorName solo el primer nombre (o "Usuario"): no se expone el correo en reseñas públicas */
public record ReviewResponse(
        UUID id,
        UUID garageId,
        UUID reservationId,
        int rating,
        String comment,
        String authorName,
        Instant createdAt
) {
}
