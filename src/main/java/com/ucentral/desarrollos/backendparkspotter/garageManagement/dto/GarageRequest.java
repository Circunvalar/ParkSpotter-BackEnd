package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public record GarageRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 1000) String description,
        @Size(max = 20) String phone,

        @NotBlank @Size(max = 255) String addressLine,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 100) String state,
        @NotBlank @Size(max = 100) String country,
        @Size(max = 20) String postalCode,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,

        @NotNull @Min(1) Integer totalSpots,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) BigDecimal pricePerHour,

        boolean open24Hours,
        LocalTime openingTime,
        LocalTime closingTime
) {
}
