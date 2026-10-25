package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

/**
 * Todos los campos son opcionales: el servicio solo actualiza los que llegan distintos de null.
 * Si un campo llega, debe cumplir las mismas reglas que en el alta (no se permiten textos vacíos).
 */
public record GarageUpdateRequest(
        @Size(min = 3, max = 150)
        @Pattern(regexp = ValidationPatterns.NOT_BLANK, message = "no puede estar vacío")
        String name,
        @Size(max = 1000) String description,
        @Pattern(regexp = ValidationPatterns.PHONE, message = "debe ser un teléfono válido (7 a 20 dígitos, puede iniciar con +)")
        String phone,

        @Size(min = 5, max = 255)
        @Pattern(regexp = ValidationPatterns.NOT_BLANK, message = "no puede estar vacío")
        String addressLine,
        @Size(min = 2, max = 100)
        @Pattern(regexp = ValidationPatterns.PLACE_NAME, message = "solo puede contener letras, espacios, puntos y guiones")
        String city,
        @Size(min = 2, max = 100)
        @Pattern(regexp = ValidationPatterns.PLACE_NAME, message = "solo puede contener letras, espacios, puntos y guiones")
        String state,
        @Size(min = 2, max = 100)
        @Pattern(regexp = ValidationPatterns.PLACE_NAME, message = "solo puede contener letras, espacios, puntos y guiones")
        String country,
        @Pattern(regexp = ValidationPatterns.POSTAL_CODE, message = "debe tener entre 3 y 10 letras, números o guiones")
        String postalCode,

        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,

        @Min(1) @Max(5000) Integer totalSpots,
        /** Desde el Sprint 03 se deriva del estado de las plazas: si llega, se rechaza con un mensaje claro. */
        @Null(message = "no se edita directamente: cambia el estado de las plazas en /api/v1/garages/{id}/spots")
        Integer availableSpots,
        @DecimalMin(value = "0.0", inclusive = true) @DecimalMax("1000000.00") @Digits(integer = 8, fraction = 2)
        BigDecimal pricePerHour,

        Boolean open24Hours,
        LocalTime openingTime,
        LocalTime closingTime
) {

    /** Si en la misma petición llegan apertura y cierre, deben ser distintos. */
    @AssertTrue(message = "openingTime y closingTime deben ser distintos (si abre todo el día, usa open24Hours=true)")
    public boolean isScheduleValid() {
        return openingTime == null || closingTime == null || !openingTime.equals(closingTime);
    }

    @AssertTrue(message = "la ubicación (0, 0) no es válida: selecciona el punto del garaje en el mapa")
    public boolean isLocationProvided() {
        return latitude == null || longitude == null || latitude != 0.0 || longitude != 0.0;
    }
}
