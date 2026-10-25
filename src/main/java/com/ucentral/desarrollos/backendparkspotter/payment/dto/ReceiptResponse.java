package com.ucentral.desarrollos.backendparkspotter.payment.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Comprobante de pago de una reserva (Sprint 05, PB-22). Los fronts lo muestran, imprimen o exportan a PDF. */
public record ReceiptResponse(
        String receiptNumber,
        Instant issuedAt,

        UUID reservationId,
        String reservationCode,
        String reservationStatus,

        String customerName,
        String customerEmail,

        String garageName,
        String garageAddress,
        String spotCode,
        VehicleType vehicleType,
        String vehiclePlate,

        Instant startTime,
        Instant endTime,
        long durationMinutes,
        BigDecimal pricePerHour,

        BigDecimal amountPaid,
        String currency,
        String paymentProvider,
        String paymentMethod,
        String paymentReference,
        String providerTransactionId,
        Instant paidAt,
        String paymentStatus
) {
}
