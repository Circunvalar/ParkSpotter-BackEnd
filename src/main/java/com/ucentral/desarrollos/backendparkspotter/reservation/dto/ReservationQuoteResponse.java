package com.ucentral.desarrollos.backendparkspotter.reservation.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cotización de una franja antes de reservar (paso de confirmación del flujo de 4 pasos).
 *
 * @param available     true si hay al menos una plaza libre para ese tipo de vehículo y franja
 * @param unavailableReason motivo cuando no se puede reservar (garaje cerrado, franja inválida...)
 */
public record ReservationQuoteResponse(
        UUID garageId,
        VehicleType vehicleType,
        Instant startTime,
        Instant endTime,
        long durationMinutes,
        BigDecimal pricePerHour,
        BigDecimal totalAmount,
        String currency,
        int availableSpots,
        boolean available,
        String unavailableReason
) {
}
