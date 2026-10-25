package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Alta de un garaje. Límites alineados con las columnas de la tabla garages.
 */
public record GarageRequest(
        @NotBlank @Size(min = 3, max = 150) String name,
        @Size(max = 1000) String description,
        @Pattern(regexp = ValidationPatterns.PHONE, message = "debe ser un teléfono válido (7 a 20 dígitos, puede iniciar con +)")
        String phone,

        @NotBlank @Size(min = 5, max = 255) String addressLine,
        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = ValidationPatterns.PLACE_NAME, message = "solo puede contener letras, espacios, puntos y guiones")
        String city,
        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = ValidationPatterns.PLACE_NAME, message = "solo puede contener letras, espacios, puntos y guiones")
        String state,
        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = ValidationPatterns.PLACE_NAME, message = "solo puede contener letras, espacios, puntos y guiones")
        String country,
        @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = "debe tener entre 3 y 10 letras, números o guiones")
        String postalCode,

        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,

        @NotNull @Min(1) @Max(5000) Integer totalSpots,
        @NotNull @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("1000000.00") @Digits(integer = 8, fraction = 2)
        BigDecimal pricePerHour,

        @NotNull Boolean open24Hours,
        LocalTime openingTime,
        LocalTime closingTime
) {

    /** Si no abre 24 horas, apertura y cierre son obligatorios y distintos (si son iguales, debe marcarse 24 horas). */
    @AssertTrue(message = "si el garaje no abre 24 horas, openingTime y closingTime son obligatorios y deben ser distintos")
    public boolean isScheduleValid() {
        if (open24Hours == null || open24Hours) {
            return true;
        }
        return openingTime != null && closingTime != null && !openingTime.equals(closingTime);
    }

    /** (0, 0) es el valor por defecto de muchos mapas: casi siempre indica que el front no envió la ubicación. */
    @AssertTrue(message = "la ubicación (0, 0) no es válida: selecciona el punto del garaje en el mapa")
    public boolean isLocationProvided() {
        return latitude == null || longitude == null || latitude != 0.0 || longitude != 0.0;
    }
}
