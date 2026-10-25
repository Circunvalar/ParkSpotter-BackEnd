package com.ucentral.desarrollos.backendparkspotter.garageManagement.service;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Construye la foto de disponibilidad de un garaje usando la hora local configurada
 * (app.garages.time-zone), para que "abierto ahora" sea consistente en web y Android.
 */
@Component
@RequiredArgsConstructor
public class GarageAvailabilityAssembler {

    private final Clock clock;

    public LocalTime now() {
        return LocalTime.now(clock);
    }

    public boolean isOpenNow(Garage garage) {
        return OpeningHours.isOpenAt(garage, now());
    }

    public GarageAvailabilityResponse snapshot(Garage garage) {
        return new GarageAvailabilityResponse(
                garage.getId(),
                garage.getStatus(),
                garage.getAvailability(),
                isOpenNow(garage),
                garage.getTotalSpots(),
                garage.getAvailableSpots(),
                garage.getOccupiedSpots(),
                garage.getReservedSpots(),
                garage.getOutOfServiceSpots(),
                Instant.now(clock)
        );
    }
}
