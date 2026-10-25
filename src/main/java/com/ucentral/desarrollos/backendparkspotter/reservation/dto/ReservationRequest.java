package com.ucentral.desarrollos.backendparkspotter.reservation.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.UUID;

/**
 * @param spotId          opcional: plaza elegida en el plano del garaje; si no llega se asigna la primera libre
 * @param searchStartedAt opcional: cuándo empezó el usuario a buscar en el front (medición PB-27)
 */
public record ReservationRequest(
        @NotNull UUID garageId,
        @NotNull VehicleType vehicleType,
        @NotBlank @Pattern(regexp = ValidationPatterns.PLATE, message = "debe ser una placa válida (4 a 10 letras, números o guiones)")
        String vehiclePlate,
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        UUID spotId,
        Instant searchStartedAt
) {
}
