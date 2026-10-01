package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageDetailResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageMapResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageMarkerResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSearchCriteria;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSortOption;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.GarageSummaryResponse;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.MapBoundsRequest;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.dto.VehicleTypeAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.service.GarageSearchService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.UUID;

import static com.ucentral.desarrollos.backendparkspotter.garageSearch.GarageSearchTestData.CENTER_LAT;
import static com.ucentral.desarrollos.backendparkspotter.garageSearch.GarageSearchTestData.CENTER_LNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas de integración de la búsqueda contra H2: validan que cada filtro se traduce bien a SQL.
 */
@SpringBootTest
@Import(GarageSearchTestData.FixedClockConfig.class)
class GarageSearchServiceTest {

    @Autowired GarageSearchService searchService;
    @Autowired GarageService garageService;
    @Autowired ParkingSpotService parkingSpotService;
    @Autowired GarageRepository garageRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    UserAccount owner;
    GarageResponse centro;
    GarageResponse nocturno;
    GarageResponse chapinero;
    GarageResponse medellin;

    @BeforeEach
    void setUp() {
        GarageSearchTestData.cleanDatabase(garageRepository, userRepository, roleRepository);
        owner = GarageSearchTestData.user(userRepository, roleRepository, "search-owner@example.com");
        centro = GarageSearchTestData.centro(garageService, owner);
        nocturno = GarageSearchTestData.nocturnoUsaquen(garageService, owner);
        chapinero = GarageSearchTestData.chapinero(garageService, owner);
        medellin = GarageSearchTestData.medellin(garageService, owner);
    }

    @AfterEach
    void tearDown() {
        garageRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- listado y filtros ----------

    @Test
    void searchWithoutFilters_ReturnsAllActiveGaragesSortedByNameWithPaginationInfo() {
        PageResponse<GarageSummaryResponse> page = searchService.search(criteria().build());

        assertThat(page.content()).extracting(GarageSummaryResponse::name)
                .containsExactly("Garaje Centro", "Garaje Medellín", "Garaje Nocturno Usaquén", "Parqueadero Chapinero");
        assertThat(page.totalElements()).isEqualTo(4);
        assertThat(page.page()).isZero();
        assertThat(page.first()).isTrue();
        assertThat(page.last()).isTrue();
        assertThat(page.content()).allMatch(summary -> summary.distanceKm() == null);
    }

    @Test
    void search_ExcludesInactiveGarages() {
        garageService.changeStatus(medellin.id(), GarageStatus.INACTIVE, owner);

        PageResponse<GarageSummaryResponse> page = searchService.search(criteria().build());

        assertThat(page.content()).extracting(GarageSummaryResponse::id).doesNotContain(medellin.id());
    }

    @Test
    void search_PaginatesWithStableOrder() {
        PageResponse<GarageSummaryResponse> first = searchService.search(criteria().page(0).size(3).build());
        PageResponse<GarageSummaryResponse> second = searchService.search(criteria().page(1).size(3).build());

        assertThat(first.content()).hasSize(3);
        assertThat(second.content()).extracting(GarageSummaryResponse::name).containsExactly("Parqueadero Chapinero");
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.last()).isFalse();
        assertThat(second.last()).isTrue();
    }

    @Test
    void textSearch_MatchesNameAddressOrCityIgnoringCase() {
        assertThat(names(criteria().q("CHAPI").build())).containsExactly("Parqueadero Chapinero");
        assertThat(names(criteria().q("calle 119").build())).containsExactly("Garaje Nocturno Usaquén");
        assertThat(names(criteria().q("medellín").build())).containsExactly("Garaje Medellín");
    }

    @Test
    void textSearch_TreatsSqlWildcardsAsPlainText() {
        assertThat(searchService.search(criteria().q("%").build()).content()).isEmpty();
        assertThat(searchService.search(criteria().q("_").build()).content()).isEmpty();
    }

    @Test
    void cityFilter_IsCaseInsensitive() {
        assertThat(names(criteria().city("bogotá").build()))
                .containsExactlyInAnyOrder("Garaje Centro", "Garaje Nocturno Usaquén", "Parqueadero Chapinero");
    }

    @Test
    void priceRange_FiltersBothBounds() {
        assertThat(names(criteria().minPrice(3000).maxPrice(4000).build()))
                .containsExactlyInAnyOrder("Garaje Centro", "Garaje Medellín");
    }

    @Test
    void open24HoursFilter_ReturnsOnlyRoundTheClockGarages() {
        assertThat(names(criteria().open24Hours(true).build()))
                .containsExactlyInAnyOrder("Garaje Centro", "Garaje Medellín");
    }

    @Test
    void openNow_AtTenAm_ExcludesNightOnlyGarage() {
        PageResponse<GarageSummaryResponse> page = searchService.search(criteria().openNow(true).build());

        assertThat(page.content()).extracting(GarageSummaryResponse::name)
                .containsExactlyInAnyOrder("Garaje Centro", "Garaje Medellín", "Parqueadero Chapinero");
        assertThat(page.content()).allMatch(GarageSummaryResponse::openNow);
    }

    @Test
    void openNowFlag_IsReportedInResultsEvenWithoutFilter() {
        GarageSummaryResponse night = searchService.search(criteria().q("Nocturno").build()).content().getFirst();

        assertThat(night.openNow()).isFalse();
    }

    @Test
    void onlyAvailable_ExcludesFullGarages() {
        occupyAll(nocturno.id());

        assertThat(names(criteria().onlyAvailable(true).build())).doesNotContain("Garaje Nocturno Usaquén");
        // Sin el filtro sigue apareciendo, marcado como lleno
        GarageSummaryResponse full = searchService.search(criteria().q("Nocturno").build()).content().getFirst();
        assertThat(full.availability()).isEqualTo(GarageAvailability.FULL);
        assertThat(full.availableSpots()).isZero();
        assertThat(full.occupiedSpots()).isEqualTo(10);
    }

    @Test
    void minAvailableSpots_FiltersBySpotsLeft() {
        assertThat(names(criteria().minAvailableSpots(20).build()))
                .containsExactlyInAnyOrder("Garaje Centro", "Parqueadero Chapinero");
    }

    @Test
    void vehicleType_ReturnsOnlyGaragesWithThatSpotType_AndOnlyAvailableChecksThoseSpots() {
        ParkingSpotResponse moto = parkingSpotService.add(chapinero.id(), new ParkingSpotRequest("M-01", 1, VehicleType.MOTORCYCLE), owner);

        assertThat(names(criteria().vehicleType(VehicleType.MOTORCYCLE).build())).containsExactly("Parqueadero Chapinero");

        parkingSpotService.changeStatus(chapinero.id(), moto.id(), SpotStatus.OCCUPIED, owner);

        assertThat(names(criteria().vehicleType(VehicleType.MOTORCYCLE).build())).containsExactly("Parqueadero Chapinero");
        assertThat(names(criteria().vehicleType(VehicleType.MOTORCYCLE).onlyAvailable(true).build())).isEmpty();
    }

    @Test
    void sortByPrice_AscAndDesc() {
        assertThat(names(criteria().sort(GarageSortOption.PRICE_ASC).build()))
                .containsExactly("Garaje Nocturno Usaquén", "Garaje Centro", "Garaje Medellín", "Parqueadero Chapinero");
        assertThat(names(criteria().sort(GarageSortOption.PRICE_DESC).build()).getFirst())
                .isEqualTo("Parqueadero Chapinero");
    }

    @Test
    void sortByAvailability_PutsMostFreeSpotsFirst() {
        assertThat(names(criteria().sort(GarageSortOption.AVAILABILITY).build()).getFirst()).isEqualTo("Garaje Centro");
    }

    // ---------- búsqueda por cercanía ----------

    @Test
    void geoSearch_DefaultRadius5Km_SortedByDistance() {
        PageResponse<GarageSummaryResponse> page = searchService.search(criteria().near(CENTER_LAT, CENTER_LNG).build());

        assertThat(page.content()).extracting(GarageSummaryResponse::name)
                .containsExactly("Garaje Centro", "Garaje Nocturno Usaquén");
        assertThat(page.content().get(0).distanceKm()).isZero();
        assertThat(page.content().get(1).distanceKm()).isBetween(4.0, 5.0);
    }

    @Test
    void geoSearch_LargerRadiusIncludesFartherGarages_ButNeverOtherCities() {
        PageResponse<GarageSummaryResponse> page = searchService.search(criteria().near(CENTER_LAT, CENTER_LNG).radiusKm(10.0).build());

        assertThat(page.content()).extracting(GarageSummaryResponse::name)
                .containsExactly("Garaje Centro", "Garaje Nocturno Usaquén", "Parqueadero Chapinero");
        assertThat(page.content()).extracting(GarageSummaryResponse::id).doesNotContain(medellin.id());
    }

    @Test
    void geoSearch_CanSortByPriceAndPaginate() {
        GarageSearchCriteriaBuilder base = criteria().near(CENTER_LAT, CENTER_LNG).radiusKm(10.0).sort(GarageSortOption.PRICE_ASC).size(2);

        PageResponse<GarageSummaryResponse> first = searchService.search(base.page(0).build());
        PageResponse<GarageSummaryResponse> second = searchService.search(base.page(1).build());

        assertThat(first.content()).extracting(GarageSummaryResponse::name).containsExactly("Garaje Nocturno Usaquén", "Garaje Centro");
        assertThat(second.content()).extracting(GarageSummaryResponse::name).containsExactly("Parqueadero Chapinero");
        assertThat(first.totalElements()).isEqualTo(3);
    }

    @Test
    void geoSearch_CombinesWithOtherFilters() {
        assertThat(names(criteria().near(CENTER_LAT, CENTER_LNG).radiusKm(10.0).openNow(true).build()))
                .containsExactly("Garaje Centro", "Parqueadero Chapinero");
    }

    // ---------- validaciones ----------

    @Test
    void latWithoutLng_IsRejected() {
        assertThatThrownBy(() -> searchService.search(criteria().lat(4.7).build())).isInstanceOf(ApiException.class);
    }

    @Test
    void radiusWithoutLocation_IsRejected() {
        assertThatThrownBy(() -> searchService.search(criteria().radiusKm(3.0).build())).isInstanceOf(ApiException.class);
    }

    @Test
    void sortByDistanceWithoutLocation_IsRejected() {
        assertThatThrownBy(() -> searchService.search(criteria().sort(GarageSortOption.DISTANCE).build()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void minPriceGreaterThanMaxPrice_IsRejected() {
        assertThatThrownBy(() -> searchService.search(criteria().minPrice(5000).maxPrice(1000).build()))
                .isInstanceOf(ApiException.class);
    }

    // ---------- mapa ----------

    @Test
    void map_ReturnsOnlyMarkersInsideVisibleArea() {
        GarageMapResponse map = searchService.mapMarkers(bogotaBounds(null), criteria().build());

        assertThat(map.markers()).extracting(GarageMarkerResponse::name)
                .containsExactlyInAnyOrder("Garaje Centro", "Garaje Nocturno Usaquén", "Parqueadero Chapinero");
        assertThat(map.count()).isEqualTo(3);
        assertThat(map.truncated()).isFalse();
    }

    @Test
    void map_WhenMoreGaragesThanLimit_FlagsTruncatedAndKeepsMostAvailable() {
        GarageMapResponse map = searchService.mapMarkers(bogotaBounds(1), criteria().build());

        assertThat(map.markers()).extracting(GarageMarkerResponse::name).containsExactly("Garaje Centro");
        assertThat(map.truncated()).isTrue();
    }

    @Test
    void map_AppliesSameFiltersAsSearch() {
        GarageMapResponse map = searchService.mapMarkers(bogotaBounds(null), criteria().maxPrice(3000).build());

        assertThat(map.markers()).extracting(GarageMarkerResponse::name).containsExactly("Garaje Nocturno Usaquén");
        assertThat(map.markers().getFirst().openNow()).isFalse();
    }

    @Test
    void map_WithInvertedBounds_IsRejected() {
        MapBoundsRequest inverted = new MapBoundsRequest(4.8, -74.2, 4.5, -73.9, null);

        assertThatThrownBy(() -> searchService.mapMarkers(inverted, criteria().build())).isInstanceOf(ApiException.class);
    }

    // ---------- detalle y disponibilidad ----------

    @Test
    void detail_IncludesAvailabilityByVehicleTypeFloorsAndOptionalSpots() {
        parkingSpotService.add(chapinero.id(), new ParkingSpotRequest("M-01", 2, VehicleType.MOTORCYCLE), owner);
        ParkingSpotResponse firstSpot = parkingSpotService.list(chapinero.id(), null, VehicleType.CAR).getFirst();
        parkingSpotService.changeStatus(chapinero.id(), firstSpot.id(), SpotStatus.RESERVED, owner);

        GarageDetailResponse withoutSpots = searchService.detail(chapinero.id(), false);
        GarageDetailResponse withSpots = searchService.detail(chapinero.id(), true);

        assertThat(withoutSpots.spots()).isEmpty();
        assertThat(withSpots.spots()).hasSize(21);
        assertThat(withSpots.floors()).containsExactly(1, 2);
        assertThat(withSpots.availability().totalSpots()).isEqualTo(21);
        assertThat(withSpots.availability().reservedSpots()).isEqualTo(1);
        assertThat(withSpots.availability().availableSpots()).isEqualTo(20);
        assertThat(withSpots.availability().openNow()).isTrue();
        assertThat(withSpots.availabilityByVehicleType())
                .containsExactlyInAnyOrder(
                        new VehicleTypeAvailability(VehicleType.CAR, 20, 19, 0, 1, 0),
                        new VehicleTypeAvailability(VehicleType.MOTORCYCLE, 1, 1, 0, 0, 0));
        assertThat(withSpots.name()).isEqualTo("Parqueadero Chapinero");
        assertThat(withSpots.pricePerHour()).isEqualByComparingTo(BigDecimal.valueOf(5000));
    }

    @Test
    void detail_UnknownGarage_ThrowsNotFound() {
        assertThatThrownBy(() -> searchService.detail(UUID.randomUUID(), false)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void availability_ReflectsSpotChangesImmediately() {
        ParkingSpotResponse spot = parkingSpotService.list(nocturno.id(), SpotStatus.AVAILABLE, null).getFirst();
        parkingSpotService.changeStatus(nocturno.id(), spot.id(), SpotStatus.OCCUPIED, owner);

        GarageAvailabilityResponse availability = searchService.availability(nocturno.id());

        assertThat(availability.availableSpots()).isEqualTo(9);
        assertThat(availability.occupiedSpots()).isEqualTo(1);
        assertThat(availability.availability()).isEqualTo(GarageAvailability.AVAILABLE);
        assertThat(availability.openNow()).isFalse();
    }

    // ---------- helpers ----------

    private void occupyAll(UUID garageId) {
        parkingSpotService.list(garageId, null, null)
                .forEach(spot -> parkingSpotService.changeStatus(garageId, spot.id(), SpotStatus.OCCUPIED, owner));
    }

    private java.util.List<String> names(GarageSearchCriteria criteria) {
        return searchService.search(criteria).content().stream().map(GarageSummaryResponse::name).toList();
    }

    private static MapBoundsRequest bogotaBounds(Integer limit) {
        return new MapBoundsRequest(4.55, -74.20, 4.80, -73.95, limit);
    }

    private static GarageSearchCriteriaBuilder criteria() {
        return new GarageSearchCriteriaBuilder();
    }

    /** Builder de prueba para no repetir los 15 parámetros del record en cada caso. */
    static final class GarageSearchCriteriaBuilder {
        private String q;
        private String city;
        private BigDecimal minPrice;
        private BigDecimal maxPrice;
        private VehicleType vehicleType;
        private Boolean onlyAvailable;
        private Integer minAvailableSpots;
        private Boolean open24Hours;
        private Boolean openNow;
        private Double lat;
        private Double lng;
        private Double radiusKm;
        private GarageSortOption sort;
        private Integer page;
        private Integer size;

        GarageSearchCriteriaBuilder q(String value) { q = value; return this; }
        GarageSearchCriteriaBuilder city(String value) { city = value; return this; }
        GarageSearchCriteriaBuilder minPrice(int value) { minPrice = BigDecimal.valueOf(value); return this; }
        GarageSearchCriteriaBuilder maxPrice(int value) { maxPrice = BigDecimal.valueOf(value); return this; }
        GarageSearchCriteriaBuilder vehicleType(VehicleType value) { vehicleType = value; return this; }
        GarageSearchCriteriaBuilder onlyAvailable(boolean value) { onlyAvailable = value; return this; }
        GarageSearchCriteriaBuilder minAvailableSpots(int value) { minAvailableSpots = value; return this; }
        GarageSearchCriteriaBuilder open24Hours(boolean value) { open24Hours = value; return this; }
        GarageSearchCriteriaBuilder openNow(boolean value) { openNow = value; return this; }
        GarageSearchCriteriaBuilder lat(double value) { lat = value; return this; }
        GarageSearchCriteriaBuilder near(double latValue, double lngValue) { lat = latValue; lng = lngValue; return this; }
        GarageSearchCriteriaBuilder radiusKm(double value) { radiusKm = value; return this; }
        GarageSearchCriteriaBuilder sort(GarageSortOption value) { sort = value; return this; }
        GarageSearchCriteriaBuilder page(int value) { page = value; return this; }
        GarageSearchCriteriaBuilder size(int value) { size = value; return this; }

        GarageSearchCriteria build() {
            return new GarageSearchCriteria(q, city, minPrice, maxPrice, vehicleType, onlyAvailable, minAvailableSpots,
                    open24Hours, openNow, lat, lng, radiusKm, sort, page, size);
        }
    }
}
