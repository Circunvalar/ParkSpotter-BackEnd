package com.ucentral.desarrollos.backendparkspotter.garageSearch.service;

/**
 * Cálculos geográficos para la búsqueda por cercanía.
 */
public final class GeoUtils {

    static final double EARTH_RADIUS_KM = 6371.0;
    private static final double KM_PER_DEGREE_LAT = 111.32;

    private GeoUtils() {
    }

    /** Distancia en km entre dos puntos usando la fórmula de Haversine. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Rectángulo que contiene el círculo de radio radiusKm alrededor del punto.
     * Se usa como pre-filtro en la base de datos; la distancia exacta se calcula después.
     */
    public static BoundingBox boundingBox(double lat, double lng, double radiusKm) {
        double latDelta = radiusKm / KM_PER_DEGREE_LAT;
        double cosLat = Math.cos(Math.toRadians(lat));
        double lngDelta = cosLat < 1e-6 ? 180.0 : radiusKm / (KM_PER_DEGREE_LAT * cosLat);
        return new BoundingBox(
                Math.max(-90.0, lat - latDelta),
                Math.min(90.0, lat + latDelta),
                Math.max(-180.0, lng - lngDelta),
                Math.min(180.0, lng + lngDelta));
    }

    /** Redondea a metros (3 decimales en km) para respuestas más limpias. */
    public static double roundKm(double km) {
        return Math.round(km * 1000.0) / 1000.0;
    }

    public record BoundingBox(double minLat, double maxLat, double minLng, double maxLng) {
    }
}
