package com.ucentral.desarrollos.backendparkspotter.reservation.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        String code,
        ReservationStatus status,

        UUID garageId,
        String garageName,
        String garageAddress,
        Double garageLatitude,
        Double garageLongitude,

        UUID spotId,
        String spotCode,
        Integer spotFloor,

        UUID driverId,
        String driverName,
        VehicleType vehicleType,
        String vehiclePlate,

        Instant startTime,
        Instant endTime,
        long durationMinutes,

        BigDecimal pricePerHour,
        BigDecimal totalAmount,
        int overtimeMinutes,
        BigDecimal overtimeAmount,
        String currency,

        Instant paymentDeadline,
        boolean paid,
        Instant paidAt,
        Instant checkInAt,
        Instant checkOutAt,
        Instant cancelledAt,
        String cancellationReason,

        boolean cancellable,
        Instant createdAt
) {
}
