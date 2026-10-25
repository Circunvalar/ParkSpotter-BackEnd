package com.ucentral.desarrollos.backendparkspotter.garageManagement.event;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;

/**
 * Se publica cada vez que cambia la disponibilidad de un garaje (estado de una plaza, capacidad
 * o estado del garaje). Lo consume el stream de tiempo real del módulo de búsqueda.
 */
public record GarageAvailabilityChangedEvent(GarageAvailabilityResponse snapshot) {
}
