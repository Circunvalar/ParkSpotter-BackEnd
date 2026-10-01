package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Área visible del mapa. Los fronts la envían cada vez que el usuario mueve o hace zoom,
 * así solo se cargan los marcadores visibles (lazy loading).
 */
public record MapBoundsRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double minLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double minLng,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double maxLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double maxLng,
        @Min(1) @Max(500) Integer limit
) {
}
