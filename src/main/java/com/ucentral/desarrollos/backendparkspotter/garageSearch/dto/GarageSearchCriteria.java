package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Filtros de búsqueda de garajes (query params). Todos son opcionales.
 *
 * @param q             texto libre: nombre, dirección o ciudad
 * @param onlyAvailable solo garajes con plazas libres (del tipo vehicleType si se indica)
 * @param openNow       solo garajes abiertos a la hora actual
 * @param lat           latitud del usuario o del centro del mapa (requiere lng)
 * @param radiusKm      radio de búsqueda; por defecto 5 km cuando llegan coordenadas
 */
public record GarageSearchCriteria(
        @Size(max = 100) String q,
        @Size(max = 100) String city,
        @DecimalMin("0.0") @DecimalMax("1000000.00") @Digits(integer = 8, fraction = 2) BigDecimal minPrice,
        @DecimalMin("0.0") @DecimalMax("1000000.00") @Digits(integer = 8, fraction = 2) BigDecimal maxPrice,
        VehicleType vehicleType,
        Boolean onlyAvailable,
        @Min(1) @Max(5000) Integer minAvailableSpots,
        Boolean open24Hours,
        Boolean openNow,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("50.0") Double radiusKm,
        GarageSortOption sort,
        @Min(0) @Max(10000) Integer page,
        @Min(1) @Max(100) Integer size
) {

    public boolean hasLocation() {
        return lat != null && lng != null;
    }
}
