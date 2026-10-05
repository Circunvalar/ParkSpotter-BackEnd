package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Todos los campos son opcionales: el servicio solo actualiza los que llegan distintos de null.
 * Pensado para PUT/PATCH sobre un garaje existente.
 */
public record GarageUpdateRequest(
        @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @Size(max = 20) String phone,

        @Size(max = 255) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 100) String country,
        @Size(max = 20) String postalCode,

        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,

        @Min(1) Integer totalSpots,
        @Min(0) Integer availableSpots,
        @DecimalMin(value = "0.0", inclusive = true) BigDecimal pricePerHour,

        Boolean open24Hours,
        LocalTime openingTime,
        LocalTime closingTime
) {
}
