package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;

public record VehicleTypeAvailability(
        VehicleType vehicleType,
        int totalSpots,
        int availableSpots,
        int occupiedSpots,
        int reservedSpots,
        int outOfServiceSpots
) {
}
