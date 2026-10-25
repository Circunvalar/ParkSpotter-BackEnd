package com.ucentral.desarrollos.backendparkspotter.garageManagement.entity;

/**
 * Nivel de disponibilidad del garaje, pensado para pintar el color del marcador en el mapa.
 */
public enum GarageAvailability {
    AVAILABLE,
    FEW_SPOTS,
    FULL,
    CLOSED;

    /** Porcentaje de plazas libres por debajo del cual se considera que quedan pocas. */
    private static final int FEW_SPOTS_PERCENT = 20;

    public static GarageAvailability of(GarageStatus status, int totalSpots, int availableSpots) {
        if (status != GarageStatus.ACTIVE) {
            return CLOSED;
        }
        if (availableSpots <= 0) {
            return FULL;
        }
        int threshold = Math.max(1, totalSpots * FEW_SPOTS_PERCENT / 100);
        return availableSpots <= threshold ? FEW_SPOTS : AVAILABLE;
    }
}
