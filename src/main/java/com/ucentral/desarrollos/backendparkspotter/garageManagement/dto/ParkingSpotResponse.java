package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;

import java.time.Instant;
import java.util.UUID;

public record ParkingSpotResponse(
        UUID id,
        UUID garageId,
        String code,
        Integer floor,
        VehicleType vehicleType,
        SpotStatus status,
        Instant updatedAt
) {
}
