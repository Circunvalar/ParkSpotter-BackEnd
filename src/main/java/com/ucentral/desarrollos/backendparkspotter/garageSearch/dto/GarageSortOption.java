package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

public enum GarageSortOption {
    /** Más cercanos primero (requiere lat y lng). Es el orden por defecto cuando llegan coordenadas. */
    DISTANCE,
    PRICE_ASC,
    PRICE_DESC,
    /** Más plazas libres primero. */
    AVAILABILITY,
    /** Orden alfabético. Es el orden por defecto sin coordenadas. */
    NAME,
    NEWEST
}
