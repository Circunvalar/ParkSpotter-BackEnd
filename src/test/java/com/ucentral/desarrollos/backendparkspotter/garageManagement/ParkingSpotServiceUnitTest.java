package com.ucentral.desarrollos.backendparkspotter.garageManagement;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.ParkingSpot;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.event.GarageAvailabilityChangedEvent;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.ParkingSpotRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageAvailabilityAssembler;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de ParkingSpotService: plazas, contadores derivados y eventos de tiempo real.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParkingSpotServiceUnitTest {

    @Mock GarageRepository garageRepository;
    @Mock ParkingSpotRepository spotRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    ParkingSpotService service;
    UserAccount owner;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T15:00:00Z"), ZoneId.of("America/Bogota"));
        service = new ParkingSpotService(garageRepository, spotRepository, new GarageAvailabilityAssembler(clock), eventPublisher);
        owner = user("owner@example.com");
        when(spotRepository.save(any(ParkingSpot.class))).thenAnswer(inv -> {
            ParkingSpot spot = inv.getArgument(0);
            if (spot.getId() == null) spot.setId(UUID.randomUUID());
            return spot;
        });
    }

    // ---------- generación y redimensionamiento ----------

    @Test
    void generateInitialSpots_ShouldCreateAvailableCarSpotsWithSequentialCodes() {
        Garage garage = newGarage();

        service.generateInitialSpots(garage, 3);

        assertThat(garage.getSpots()).extracting(ParkingSpot::getCode).containsExactly("P-001", "P-002", "P-003");
        assertThat(garage.getSpots()).allMatch(spot -> spot.getStatus() == SpotStatus.AVAILABLE
                && spot.getVehicleType() == VehicleType.CAR && spot.getFloor() == 1);
        assertThat(garage.getTotalSpots()).isEqualTo(3);
        assertThat(garage.getAvailableSpots()).isEqualTo(3);
        // Garaje nuevo (sin id): las plazas se guardan por cascada, no una a una
        verify(spotRepository, never()).save(any());
    }

    @Test
    void resize_WhenGrowing_ShouldAddAvailableSpotsWithoutRepeatingCodes() {
        Garage garage = persistedGarage(2);

        service.resize(garage, 4);

        assertThat(garage.getSpots()).extracting(ParkingSpot::getCode).containsExactlyInAnyOrder("P-001", "P-002", "P-003", "P-004");
        assertThat(garage.getAvailableSpots()).isEqualTo(4);
    }

    @Test
    void resize_WhenShrinking_ShouldRemoveOutOfServiceFirstThenFreeSpots() {
        Garage garage = persistedGarage(4);
        spot(garage, "P-001").setStatus(SpotStatus.OCCUPIED);
        spot(garage, "P-002").setStatus(SpotStatus.OUT_OF_SERVICE);

        service.resize(garage, 2);

        assertThat(garage.getSpots()).extracting(ParkingSpot::getCode).containsExactlyInAnyOrder("P-001", "P-003");
        assertThat(garage.getTotalSpots()).isEqualTo(2);
        assertThat(garage.getOccupiedSpots()).isEqualTo(1);
        assertThat(garage.getAvailableSpots()).isEqualTo(1);
    }

    @Test
    void resize_WhenShrinkingWouldRemoveOccupiedOrReservedSpots_ShouldThrow() {
        Garage garage = persistedGarage(3);
        spot(garage, "P-001").setStatus(SpotStatus.OCCUPIED);
        spot(garage, "P-002").setStatus(SpotStatus.RESERVED);

        assertThatThrownBy(() -> service.resize(garage, 1)).isInstanceOf(ApiException.class);
        assertThat(garage.getSpots()).hasSize(3);
    }

    @Test
    void resize_ForLegacyGarageWithoutSpots_ShouldFirstRebuildSpotsFromCounters() {
        Garage garage = legacyGarage(10, 4);

        service.resize(garage, 12);

        assertThat(garage.getTotalSpots()).isEqualTo(12);
        assertThat(garage.getOccupiedSpots()).isEqualTo(6);
        assertThat(garage.getAvailableSpots()).isEqualTo(6);
    }

    @Test
    void backfillLegacyGarages_ShouldGenerateSpotsRespectingAvailableAndOccupied() {
        Garage legacy = legacyGarage(5, 2);
        when(garageRepository.findGaragesWithoutSpots()).thenReturn(List.of(legacy));

        int processed = service.backfillLegacyGarages();

        assertThat(processed).isEqualTo(1);
        assertThat(legacy.getSpots()).hasSize(5);
        assertThat(legacy.getAvailableSpots()).isEqualTo(2);
        assertThat(legacy.getOccupiedSpots()).isEqualTo(3);
    }

    // ---------- estado en tiempo real ----------

    @Test
    void changeStatus_ShouldRecalculateCountersAndPublishAvailabilityEvent() {
        Garage garage = persistedGarage(2);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));
        ParkingSpot target = spot(garage, "P-001");

        ParkingSpotResponse response = service.changeStatus(garage.getId(), target.getId(), SpotStatus.OCCUPIED, owner);

        assertThat(response.status()).isEqualTo(SpotStatus.OCCUPIED);
        assertThat(garage.getAvailableSpots()).isEqualTo(1);
        assertThat(garage.getOccupiedSpots()).isEqualTo(1);

        ArgumentCaptor<GarageAvailabilityChangedEvent> captor = ArgumentCaptor.forClass(GarageAvailabilityChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().snapshot().garageId()).isEqualTo(garage.getId());
        assertThat(captor.getValue().snapshot().availableSpots()).isEqualTo(1);
        assertThat(captor.getValue().snapshot().occupiedSpots()).isEqualTo(1);
    }

    @Test
    void changeStatus_WhenAllSpotsTaken_ShouldMarkGarageAsFull() {
        Garage garage = persistedGarage(2);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        service.changeStatus(garage.getId(), spot(garage, "P-001").getId(), SpotStatus.OCCUPIED, owner);
        service.changeStatus(garage.getId(), spot(garage, "P-002").getId(), SpotStatus.RESERVED, owner);

        assertThat(garage.getAvailability()).isEqualTo(GarageAvailability.FULL);
        assertThat(garage.getReservedSpots()).isEqualTo(1);
    }

    @Test
    void changeStatus_WhenStatusIsTheSame_ShouldNotPublishEvent() {
        Garage garage = persistedGarage(1);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        service.changeStatus(garage.getId(), spot(garage, "P-001").getId(), SpotStatus.AVAILABLE, owner);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void changeStatus_WhenUserIsNotOwnerNorAdmin_ShouldThrowAccessDenied() {
        Garage garage = persistedGarage(1);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        assertThatThrownBy(() -> service.changeStatus(garage.getId(), spot(garage, "P-001").getId(),
                SpotStatus.OCCUPIED, user("stranger@example.com")))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(spot(garage, "P-001").getStatus()).isEqualTo(SpotStatus.AVAILABLE);
    }

    @Test
    void changeStatus_WhenAdmin_ShouldSucceed() {
        Garage garage = persistedGarage(1);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));
        UserAccount admin = user("admin@example.com");
        admin.getRoles().add(role("ROLE_ADMIN"));

        ParkingSpotResponse response = service.changeStatus(garage.getId(), spot(garage, "P-001").getId(), SpotStatus.OUT_OF_SERVICE, admin);

        assertThat(response.status()).isEqualTo(SpotStatus.OUT_OF_SERVICE);
        assertThat(garage.getOutOfServiceSpots()).isEqualTo(1);
    }

    @Test
    void changeStatus_WhenSpotBelongsToAnotherGarage_ShouldThrowNotFound() {
        Garage garage = persistedGarage(1);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        assertThatThrownBy(() -> service.changeStatus(garage.getId(), UUID.randomUUID(), SpotStatus.OCCUPIED, owner))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- alta, edición y borrado ----------

    @Test
    void add_WithoutCode_ShouldGenerateNextCodeAndIncreaseCapacity() {
        Garage garage = persistedGarage(2);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        ParkingSpotResponse response = service.add(garage.getId(), new ParkingSpotRequest(null, 2, VehicleType.MOTORCYCLE), owner);

        assertThat(response.code()).isEqualTo("P-003");
        assertThat(response.floor()).isEqualTo(2);
        assertThat(response.vehicleType()).isEqualTo(VehicleType.MOTORCYCLE);
        assertThat(garage.getTotalSpots()).isEqualTo(3);
        verify(eventPublisher).publishEvent(any(GarageAvailabilityChangedEvent.class));
    }

    @Test
    void add_WithDuplicatedCode_ShouldThrow() {
        Garage garage = persistedGarage(2);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        assertThatThrownBy(() -> service.add(garage.getId(), new ParkingSpotRequest("p-001", null, null), owner))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void update_ShouldChangeCodeFloorAndVehicleType() {
        Garage garage = persistedGarage(2);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));
        ParkingSpot target = spot(garage, "P-002");

        ParkingSpotResponse response = service.update(garage.getId(), target.getId(),
                new ParkingSpotRequest("e-01", -1, VehicleType.ELECTRIC), owner);

        assertThat(response.code()).isEqualTo("E-01");
        assertThat(response.floor()).isEqualTo(-1);
        assertThat(response.vehicleType()).isEqualTo(VehicleType.ELECTRIC);
    }

    @Test
    void delete_WhenSpotIsOccupied_ShouldThrow() {
        Garage garage = persistedGarage(2);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));
        ParkingSpot target = spot(garage, "P-001");
        target.setStatus(SpotStatus.OCCUPIED);

        assertThatThrownBy(() -> service.delete(garage.getId(), target.getId(), owner)).isInstanceOf(ApiException.class);
        assertThat(garage.getSpots()).hasSize(2);
    }

    @Test
    void delete_WhenLastSpot_ShouldThrow() {
        Garage garage = persistedGarage(1);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        assertThatThrownBy(() -> service.delete(garage.getId(), spot(garage, "P-001").getId(), owner))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void delete_WhenFreeSpot_ShouldRemoveAndDecreaseCapacity() {
        Garage garage = persistedGarage(3);
        when(garageRepository.findByIdForUpdate(garage.getId())).thenReturn(Optional.of(garage));

        service.delete(garage.getId(), spot(garage, "P-002").getId(), owner);

        assertThat(garage.getSpots()).extracting(ParkingSpot::getCode).containsExactly("P-001", "P-003");
        assertThat(garage.getTotalSpots()).isEqualTo(2);
    }

    @Test
    void list_WhenGarageDoesNotExist_ShouldThrowNotFound() {
        UUID id = UUID.randomUUID();
        when(garageRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> service.list(id, null, null)).isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- helpers ----------

    private Garage newGarage() {
        Garage garage = new Garage();
        garage.setOwner(owner);
        garage.setName("Garaje Central");
        garage.setLatitude(4.710989);
        garage.setLongitude(-74.072092);
        garage.setPricePerHour(BigDecimal.valueOf(3500));
        garage.setOpen24Hours(true);
        garage.setStatus(GarageStatus.ACTIVE);
        garage.setTotalSpots(0);
        garage.setAvailableSpots(0);
        return garage;
    }

    /** Garaje ya guardado (con id) y con sus plazas generadas. */
    private Garage persistedGarage(int spots) {
        Garage garage = newGarage();
        service.generateInitialSpots(garage, spots);
        garage.setId(UUID.randomUUID());
        garage.getSpots().forEach(spot -> spot.setId(UUID.randomUUID()));
        return garage;
    }

    /** Garaje del Sprint 02: solo contadores, sin plazas. */
    private Garage legacyGarage(int total, int available) {
        Garage garage = newGarage();
        garage.setId(UUID.randomUUID());
        garage.setTotalSpots(total);
        garage.setAvailableSpots(available);
        return garage;
    }

    private ParkingSpot spot(Garage garage, String code) {
        return garage.getSpots().stream().filter(s -> s.getCode().equals(code)).findFirst().orElseThrow();
    }

    private UserAccount user(String email) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash("hashed-pw");
        user.setEnabled(true);
        return user;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        return role;
    }
}
