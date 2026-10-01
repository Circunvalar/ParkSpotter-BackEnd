package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Marcador liviano para el mapa: solo lo que se pinta en el pin y su popup.
 */
public record GarageMarkerResponse(
        UUID id,
        String name,
        Double latitude,
        Double longitude,
        BigDecimal pricePerHour,
        int availableSpots,
        int totalSpots,
        GarageAvailability availability,
        boolean openNow
) {
}
