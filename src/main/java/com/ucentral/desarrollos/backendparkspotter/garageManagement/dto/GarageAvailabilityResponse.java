package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto de la disponibilidad de un garaje. Es lo que se envía por el stream en tiempo real
 * y lo que devuelve GET /api/v1/garages/{id}/availability.
 */
public record GarageAvailabilityResponse(
        UUID garageId,
        GarageStatus status,
        GarageAvailability availability,
        boolean openNow,
        int totalSpots,
        int availableSpots,
        int occupiedSpots,
        int reservedSpots,
        int outOfServiceSpots,
        Instant timestamp
) {
}
