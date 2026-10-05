package com.ucentral.desarrollos.backendparkspotter.garageSearch.dto;

import java.util.List;

/**
 * @param truncated true si en el área había más garajes que el límite pedido: el front debería pedir más zoom
 */
public record GarageMapResponse(
        List<GarageMarkerResponse> markers,
        int count,
        boolean truncated
) {
}
