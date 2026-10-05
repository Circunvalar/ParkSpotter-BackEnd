package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.garageSearch.service.GeoUtils;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.service.GeoUtils.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoUtilsTest {

    @Test
    void haversine_BogotaToMedellin_IsAbout240Km() {
        double km = GeoUtils.haversineKm(4.710989, -74.072092, 6.244203, -75.581212);

        assertThat(km).isCloseTo(240.0, within(5.0));
    }

    @Test
    void haversine_SamePoint_IsZero() {
        assertThat(GeoUtils.haversineKm(4.7, -74.0, 4.7, -74.0)).isZero();
    }

    @Test
    void boundingBox_ContainsEveryPointInsideTheRadius() {
        double lat = 4.710989;
        double lng = -74.072092;
        BoundingBox box = GeoUtils.boundingBox(lat, lng, 5);

        // Puntos a ~4.9 km en las cuatro direcciones deben quedar dentro del rectángulo
        double latOffset = 4.9 / 111.32;
        double lngOffset = 4.9 / (111.32 * Math.cos(Math.toRadians(lat)));
        assertThat(lat + latOffset).isBetween(box.minLat(), box.maxLat());
        assertThat(lat - latOffset).isBetween(box.minLat(), box.maxLat());
        assertThat(lng + lngOffset).isBetween(box.minLng(), box.maxLng());
        assertThat(lng - lngOffset).isBetween(box.minLng(), box.maxLng());
    }

    @Test
    void boundingBox_IsClampedToValidCoordinates() {
        BoundingBox box = GeoUtils.boundingBox(89.99, 179.99, 50);

        assertThat(box.maxLat()).isLessThanOrEqualTo(90.0);
        assertThat(box.maxLng()).isLessThanOrEqualTo(180.0);
        assertThat(box.minLng()).isGreaterThanOrEqualTo(-180.0);
    }

    @Test
    void boundingBox_AtThePole_CoversEveryLongitude() {
        BoundingBox box = GeoUtils.boundingBox(90.0, 0.0, 5);

        assertThat(box.minLng()).isEqualTo(-180.0);
        assertThat(box.maxLng()).isEqualTo(180.0);
    }

    @Test
    void roundKm_RoundsToMeters() {
        assertThat(GeoUtils.roundKm(1.23456)).isEqualTo(1.235);
    }
}
