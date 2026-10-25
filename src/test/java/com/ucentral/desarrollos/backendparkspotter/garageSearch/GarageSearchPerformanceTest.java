package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageMapResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSearchCriteria;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSummaryResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.MapBoundsRequest;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.service.GarageSearchService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static com.ucentral.desarrollos.backendparkspotter.garageSearch.GarageSearchTestData.CENTER_LAT;
import static com.ucentral.desarrollos.backendparkspotter.garageSearch.GarageSearchTestData.CENTER_LNG;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF de rendimiento (ISO/IEC 25010 - Performance Efficiency): la búsqueda debe responder en menos de 3 s.
 * Se cargan 1.000 garajes repartidos por Bogotá y se miden las consultas más pesadas.
 */
@SpringBootTest
@Import(GarageSearchTestData.FixedClockConfig.class)
class GarageSearchPerformanceTest {

    private static final int GARAGES = 1000;
    private static final Duration MAX_RESPONSE_TIME = Duration.ofSeconds(3);

    @Autowired GarageSearchService searchService;
    @Autowired ParkingSpotService parkingSpotService;
    @Autowired GarageRepository garageRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    @BeforeEach
    void seed() {
        GarageSearchTestData.cleanDatabase(garageRepository, userRepository, roleRepository);
        UserAccount owner = GarageSearchTestData.user(userRepository, roleRepository, "perf-owner@example.com");
        Random random = new Random(42);
        List<Garage> garages = new ArrayList<>();
        for (int i = 0; i < GARAGES; i++) {
            Garage garage = new Garage();
            garage.setOwner(owner);
            garage.setName("Garaje " + i);
            garage.setAddressLine("Calle " + i);
            garage.setCity("Bogotá");
            garage.setState("Cundinamarca");
            garage.setCountry("Colombia");
            // ~ ±15 km alrededor del centro
            garage.setLatitude(CENTER_LAT + (random.nextDouble() - 0.5) * 0.27);
            garage.setLongitude(CENTER_LNG + (random.nextDouble() - 0.5) * 0.27);
            garage.setPricePerHour(BigDecimal.valueOf(2000 + random.nextInt(5000)));
            garage.setOpen24Hours(random.nextBoolean());
            if (!garage.isOpen24Hours()) {
                garage.setOpeningTime(LocalTime.of(6, 0));
                garage.setClosingTime(LocalTime.of(22, 0));
            }
            garage.setStatus(GarageStatus.ACTIVE);
            parkingSpotService.generateInitialSpots(garage, 5 + random.nextInt(20));
            garages.add(garage);
        }
        garageRepository.saveAll(garages);
    }

    @AfterEach
    void tearDown() {
        garageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void geoSearchWithFilters_RespondsUnderThreeSeconds() {
        GarageSearchCriteria criteria = new GarageSearchCriteria(null, null, null, BigDecimal.valueOf(6000), null,
                true, null, null, true, CENTER_LAT, CENTER_LNG, 10.0, null, 0, 20);

        PageResponse<GarageSummaryResponse> page = timed(() -> searchService.search(criteria));

        assertThat(page.content()).isNotEmpty();
    }

    @Test
    void textSearchWithPagination_RespondsUnderThreeSeconds() {
        GarageSearchCriteria criteria = new GarageSearchCriteria("garaje 1", "Bogotá", null, null, null,
                null, null, null, null, null, null, null, null, 2, 20);

        PageResponse<GarageSummaryResponse> page = timed(() -> searchService.search(criteria));

        assertThat(page.totalElements()).isPositive();
    }

    @Test
    void mapMarkers_RespondsUnderThreeSeconds() {
        MapBoundsRequest bounds = new MapBoundsRequest(4.55, -74.25, 4.85, -73.90, 500);
        GarageSearchCriteria noFilters = new GarageSearchCriteria(null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);

        GarageMapResponse map = timed(() -> searchService.mapMarkers(bounds, noFilters));

        assertThat(map.count()).isEqualTo(500);
        assertThat(map.truncated()).isTrue();
    }

    private static <T> T timed(Supplier<T> call) {
        // Primera llamada para calentar (JIT, caché de consultas); se mide la segunda.
        call.get();
        long start = System.nanoTime();
        T result = call.get();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed).isLessThan(MAX_RESPONSE_TIME);
        return result;
    }
}
