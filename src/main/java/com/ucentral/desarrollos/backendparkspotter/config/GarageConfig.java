package com.ucentral.desarrollos.backendparkspotter.config;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableScheduling
public class GarageConfig {

    /** Reloj en la zona horaria de los garajes: define qué significa "abierto ahora". */
    @Bean
    public Clock clock(@Value("${app.garages.time-zone:America/Bogota}") ZoneId zoneId) {
        return Clock.system(zoneId);
    }

    /** Genera las plazas de los garajes creados antes del Sprint 03 (idempotente). */
    @Bean
    @ConditionalOnProperty(name = "app.garages.backfill-spots", havingValue = "true", matchIfMissing = true)
    ApplicationRunner backfillParkingSpots(ParkingSpotService parkingSpotService) {
        return args -> parkingSpotService.backfillLegacyGarages();
    }
}
