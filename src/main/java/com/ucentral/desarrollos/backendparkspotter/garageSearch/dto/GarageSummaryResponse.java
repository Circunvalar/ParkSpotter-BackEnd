package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Elemento del listado de búsqueda: lo necesario para la tarjeta del garaje en la lista.
 * distanceKm solo viene cuando la búsqueda incluye lat y lng.
 */
public record GarageSummaryResponse(
        UUID id,
        String name,
        String addressLine,
        String city,
        Double latitude,
        Double longitude,
        BigDecimal pricePerHour,
        int totalSpots,
        int availableSpots,
        int occupiedSpots,
        int reservedSpots,
        GarageAvailability availability,
        boolean open24Hours,
        LocalTime openingTime,
        LocalTime closingTime,
        boolean openNow,
        Double distanceKm
) {
}
