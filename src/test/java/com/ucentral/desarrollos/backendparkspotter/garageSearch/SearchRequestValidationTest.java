package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSearchCriteria;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.MapBoundsRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationTestSupport.invalidFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criterios de validación de los parámetros de búsqueda y del mapa.
 */
class SearchRequestValidationTest {

    @Test
    void noFilters_IsValid() {
        assertThat(invalidFields(criteria(null, null, null, null, null, null, null))).isEmpty();
    }

    @Test
    void pagination_IsBounded() {
        assertThat(invalidFields(criteria(-1, null, null, null, null, null, null))).contains("page");
        assertThat(invalidFields(criteria(10_001, null, null, null, null, null, null))).contains("page");
        assertThat(invalidFields(criteria(null, 0, null, null, null, null, null))).contains("size");
        assertThat(invalidFields(criteria(null, 101, null, null, null, null, null))).contains("size");
    }

    @Test
    void location_AndRadius_AreBounded() {
        assertThat(invalidFields(criteria(null, null, 91.0, 0.0, null, null, null))).contains("lat");
        assertThat(invalidFields(criteria(null, null, 0.0, 181.0, null, null, null))).contains("lng");
        assertThat(invalidFields(criteria(null, null, 4.7, -74.0, 0.0, null, null))).contains("radiusKm");
        assertThat(invalidFields(criteria(null, null, 4.7, -74.0, 50.1, null, null))).contains("radiusKm");
    }

    @Test
    void prices_AreNonNegativeWithTwoDecimals() {
        assertThat(invalidFields(criteria(null, null, null, null, null, new BigDecimal("-1"), null))).contains("minPrice");
        assertThat(invalidFields(criteria(null, null, null, null, null, null, new BigDecimal("10.123")))).contains("maxPrice");
    }

    @Test
    void textFilters_AreLimitedTo100Characters() {
        GarageSearchCriteria longText = new GarageSearchCriteria("x".repeat(101), "y".repeat(101), null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        assertThat(invalidFields(longText)).contains("q", "city");
    }

    @Test
    void mapBounds_AreRequiredAndLimitIsBounded() {
        assertThat(invalidFields(new MapBoundsRequest(null, null, null, null, null)))
                .containsExactlyInAnyOrder("minLat", "minLng", "maxLat", "maxLng");
        assertThat(invalidFields(new MapBoundsRequest(4.5, -74.2, 4.8, -73.9, 501))).contains("limit");
        assertThat(invalidFields(new MapBoundsRequest(4.5, -74.2, 4.8, -73.9, 200))).isEmpty();
    }

    private static GarageSearchCriteria criteria(Integer page, Integer size, Double lat, Double lng, Double radiusKm,
                                                 BigDecimal minPrice, BigDecimal maxPrice) {
        return new GarageSearchCriteria(null, null, minPrice, maxPrice, null, null, null, null, null,
                lat, lng, radiusKm, null, page, size);
    }
}
