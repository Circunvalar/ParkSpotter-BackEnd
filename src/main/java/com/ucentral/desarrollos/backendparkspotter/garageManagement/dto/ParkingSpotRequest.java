package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de una plaza. En el alta, si no llega code se genera uno (P-001, P-002...);
 * en la edición, los campos null se conservan.
 *
 * @param floor piso o nivel: negativos para sótanos (-10 a 200)
 */
public record ParkingSpotRequest(
        @Size(min = 1, max = 20)
        @Pattern(regexp = ValidationPatterns.SPOT_CODE, message = "solo puede contener letras, números y guiones (ej. P-001)")
        String code,
        @Min(-10) @Max(200) Integer floor,
        VehicleType vehicleType
) {
}
