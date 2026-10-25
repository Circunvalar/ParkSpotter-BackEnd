package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Datos de prueba compartidos por las pruebas de búsqueda: garajes reales alrededor del centro de Bogotá.
 */
final class GarageSearchTestData {

    static final double CENTER_LAT = 4.710989;
    static final double CENTER_LNG = -74.072092;

    private GarageSearchTestData() {
    }

    /** Reloj fijo: 30/09/2026 10:00 hora de Bogotá, para que "abierto ahora" sea determinista. */
    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Bogota"));
        }
    }

    static void cleanDatabase(GarageRepository garageRepository, UserRepository userRepository, RoleRepository roleRepository) {
        garageRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        Role role = new Role();
        role.setName("ROLE_USER");
        roleRepository.save(role);
    }

    static UserAccount user(UserRepository userRepository, RoleRepository roleRepository, String email) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash("$2a$12$hashdepruebahashdepruebahashdepruebahashdepruebahashde");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByName("ROLE_USER").orElseThrow());
        return userRepository.save(user);
    }

    /** Centro de Bogotá, 24 horas, $3.500, 50 plazas. */
    static GarageResponse centro(GarageService service, UserAccount owner) {
        return service.create(request("Garaje Centro", "Cra 7 # 12-30", "Bogotá",
                CENTER_LAT, CENTER_LNG, 50, 3500, true, null, null), owner);
    }

    /** Usaquén, ~4.7 km del centro, solo de noche (18:00 - 06:00), $2.500, 10 plazas. */
    static GarageResponse nocturnoUsaquen(GarageService service, UserAccount owner) {
        return service.create(request("Garaje Nocturno Usaquén", "Calle 119 # 6-20", "Bogotá",
                4.7030, -74.0300, 10, 2500, false, LocalTime.of(18, 0), LocalTime.of(6, 0)), owner);
    }

    /** Chapinero, ~7 km del centro, 06:00 - 22:00, $5.000, 20 plazas. */
    static GarageResponse chapinero(GarageService service, UserAccount owner) {
        return service.create(request("Parqueadero Chapinero", "Calle 57 # 9-15", "Bogotá",
                4.6486, -74.0628, 20, 5000, false, LocalTime.of(6, 0), LocalTime.of(22, 0)), owner);
    }

    /** Medellín, ~240 km: nunca debe salir en búsquedas por cercanía en Bogotá. */
    static GarageResponse medellin(GarageService service, UserAccount owner) {
        return service.create(request("Garaje Medellín", "Cra 50 # 10-10", "Medellín",
                6.244203, -75.581212, 10, 4000, true, null, null), owner);
    }

    static GarageRequest request(String name, String address, String city, double lat, double lng,
                                 int spots, int price, boolean open24Hours, LocalTime opening, LocalTime closing) {
        return new GarageRequest(
                name, "Parqueadero de prueba", "3000000000",
                address, city, "Cundinamarca", "Colombia", "110111",
                lat, lng,
                spots, BigDecimal.valueOf(price),
                open24Hours, opening, closing
        );
    }
}
