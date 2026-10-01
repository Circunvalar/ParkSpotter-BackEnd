package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Vista de detalle del garaje: datos, horario, disponibilidad total, disponibilidad por tipo
 * de vehículo y (opcional, includeSpots=true) el listado de plazas para dibujar el parqueadero.
 */
public record GarageDetailResponse(
        UUID id,
        String name,
        String description,
        String phone,

        String addressLine,
        String city,
        String state,
        String country,
        String postalCode,
        Double latitude,
        Double longitude,

        BigDecimal pricePerHour,
        boolean open24Hours,
        LocalTime openingTime,
        LocalTime closingTime,

        GarageAvailabilityResponse availability,
        List<VehicleTypeAvailability> availabilityByVehicleType,
        List<Integer> floors,
        List<ParkingSpotResponse> spots,

        Instant createdAt,
        Instant updatedAt
) {
}
