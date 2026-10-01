package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de una plaza. En el alta, si no llega code se genera uno (P-001, P-002...);
 * en la edición, los campos null se conservan.
 */
public record ParkingSpotRequest(
        @Size(min = 1, max = 20) @Pattern(regexp = "^[A-Za-z0-9-]+$") String code,
        @Min(-10) @Max(200) Integer floor,
        VehicleType vehicleType
) {
}
