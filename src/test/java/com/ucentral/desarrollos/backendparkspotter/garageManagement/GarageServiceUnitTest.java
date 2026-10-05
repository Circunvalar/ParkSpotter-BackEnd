package com.ucentral.desarrollos.backendparkspotter.garageManagement;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageUpdateRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias puras de GarageService: el repositorio va mockeado
 * (sin Spring, sin base de datos). Cada método se puede correr individualmente con @Test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GarageServiceUnitTest {

    @Mock GarageRepository garageRepository;

    @InjectMocks
    GarageService garageService;

    @BeforeEach
    void defaultStubs() {
        when(garageRepository.save(any(Garage.class))).thenAnswer(inv -> {
            Garage g = inv.getArgument(0);
            if (g.getId() == null) g.setId(UUID.randomUUID());
            return g;
        });
    }

    // ---------- create ----------

    @Test
    void create_ShouldSetOwnerAvailableSpotsEqualToTotalAndStatusActive() {
        UserAccount owner = user("owner@example.com");

        GarageResponse response = garageService.create(validRequest(true, null, null), owner);

        assertThat(response.ownerId()).isEqualTo(owner.getId());
        assertThat(response.ownerEmail()).isEqualTo(owner.getEmail());
        assertThat(response.totalSpots()).isEqualTo(50);
        assertThat(response.availableSpots()).isEqualTo(50);
        assertThat(response.status()).isEqualTo(GarageStatus.ACTIVE);

        ArgumentCaptor<Garage> captor = ArgumentCaptor.forClass(Garage.class);
        verify(garageRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isEqualTo(owner);
    }

    @Test
    void create_WhenNotOpen24AndScheduleMissing_ShouldThrow_AndNeverSave() {
        UserAccount owner = user("owner2@example.com");

        assertThatThrownBy(() -> garageService.create(validRequest(false, null, null), owner))
                .isInstanceOf(ApiException.class);

        verify(garageRepository, never()).save(any());
    }

    @Test
    void create_WhenNotOpen24AndScheduleProvided_ShouldPersistOpeningAndClosingTimes() {
        UserAccount owner = user("owner3@example.com");
        LocalTime opening = LocalTime.of(8, 0);
        LocalTime closing = LocalTime.of(20, 0);

        GarageResponse response = garageService.create(validRequest(false, opening, closing), owner);

        assertThat(response.open24Hours()).isFalse();
        assertThat(response.openingTime()).isEqualTo(opening);
        assertThat(response.closingTime()).isEqualTo(closing);
    }

    // ---------- getById ----------

    @Test
    void getById_WhenFound_ShouldMapAllFieldsToResponse() {
        UserAccount owner = user("owner4@example.com");
        Garage garage = garage(owner, 30, 30, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageResponse response = garageService.getById(garage.getId());

        assertThat(response.id()).isEqualTo(garage.getId());
        assertThat(response.name()).isEqualTo(garage.getName());
        assertThat(response.city()).isEqualTo(garage.getCity());
        assertThat(response.latitude()).isEqualTo(garage.getLatitude());
        assertThat(response.longitude()).isEqualTo(garage.getLongitude());
        assertThat(response.ownerEmail()).isEqualTo(owner.getEmail());
    }

    @Test
    void getById_WhenNotFound_ShouldThrowEntityNotFoundException() {
        UUID randomId = UUID.randomUUID();
        when(garageRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> garageService.getById(randomId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------- update ----------

    @Test
    void update_WhenOwnerMatches_ShouldApplyOnlyProvidedFields_AndKeepRest() {
        UserAccount owner = user("owner5@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageUpdateRequest update = new GarageUpdateRequest(
                "Nuevo nombre", null, null, null, null, null, null, null,
                null, null, null, null, BigDecimal.valueOf(5000), null, null, null
        );

        GarageResponse updated = garageService.update(garage.getId(), update, owner);

        assertThat(updated.name()).isEqualTo("Nuevo nombre");
        assertThat(updated.pricePerHour()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        assertThat(updated.city()).isEqualTo(garage.getCity());
    }

    @Test
    void update_WhenTotalSpotsReducedBelowCurrentAvailable_ShouldClampAvailableSpots() {
        UserAccount owner = user("owner6@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageUpdateRequest update = new GarageUpdateRequest(
                null, null, null, null, null, null, null, null,
                null, null, 10, null, null, null, null, null
        );

        GarageResponse updated = garageService.update(garage.getId(), update, owner);

        assertThat(updated.totalSpots()).isEqualTo(10);
        assertThat(updated.availableSpots()).isEqualTo(10);
    }

    @Test
    void update_WhenAvailableSpotsGreaterThanTotalSpots_ShouldThrow_AndNotSave() {
        UserAccount owner = user("owner7@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageUpdateRequest update = new GarageUpdateRequest(
                null, null, null, null, null, null, null, null,
                null, null, null, 100, null, null, null, null
        );

        assertThatThrownBy(() -> garageService.update(garage.getId(), update, owner))
                .isInstanceOf(ApiException.class);

        verify(garageRepository, never()).save(any());
    }

    @Test
    void update_WhenUserNeitherOwnerNorAdmin_ShouldThrowAccessDenied_AndNotSave() {
        UserAccount owner = user("owner8@example.com");
        UserAccount stranger = user("stranger@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageUpdateRequest update = new GarageUpdateRequest(
                "Hackeado", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> garageService.update(garage.getId(), update, stranger))
                .isInstanceOf(AccessDeniedException.class);

        verify(garageRepository, never()).save(any());
    }

    @Test
    void update_WhenUserIsAdminButNotOwner_ShouldSucceed() {
        UserAccount owner = user("owner9@example.com");
        UserAccount admin = user("admin@example.com");
        admin.getRoles().add(role("ROLE_ADMIN"));
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageUpdateRequest update = new GarageUpdateRequest(
                "Editado por admin", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        GarageResponse updated = garageService.update(garage.getId(), update, admin);

        assertThat(updated.name()).isEqualTo("Editado por admin");
    }

    @Test
    void update_WhenSwitchingToOpen24Hours_ShouldClearOpeningAndClosingTimes() {
        UserAccount owner = user("owner10@example.com");
        Garage garage = garage(owner, 50, 50, false, GarageStatus.ACTIVE); // tiene horario fijo
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageUpdateRequest update = new GarageUpdateRequest(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, true, null, null
        );

        GarageResponse updated = garageService.update(garage.getId(), update, owner);

        assertThat(updated.open24Hours()).isTrue();
        assertThat(updated.openingTime()).isNull();
        assertThat(updated.closingTime()).isNull();
    }

    @Test
    void update_WhenTurningOff24HoursWithoutNewSchedule_ShouldThrow() {
        UserAccount owner = user("owner11@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE); // 24h, sin horario fijo
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageUpdateRequest update = new GarageUpdateRequest(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, false, null, null
        );

        assertThatThrownBy(() -> garageService.update(garage.getId(), update, owner))
                .isInstanceOf(ApiException.class);
    }

    // ---------- changeStatus ----------

    @Test
    void changeStatus_WhenOwner_ShouldUpdateAndSave() {
        UserAccount owner = user("owner12@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        GarageResponse response = garageService.changeStatus(garage.getId(), GarageStatus.INACTIVE, owner);

        assertThat(response.status()).isEqualTo(GarageStatus.INACTIVE);
    }

    @Test
    void changeStatus_WhenNotOwnerNorAdmin_ShouldThrowAccessDenied() {
        UserAccount owner = user("owner13@example.com");
        UserAccount stranger = user("stranger2@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        assertThatThrownBy(() -> garageService.changeStatus(garage.getId(), GarageStatus.SUSPENDED, stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---------- delete ----------

    @Test
    void delete_WhenOwner_ShouldCallRepositoryDelete() {
        UserAccount owner = user("owner14@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        garageService.delete(garage.getId(), owner);

        verify(garageRepository, times(1)).delete(garage);
    }

    @Test
    void delete_WhenNotOwnerNorAdmin_ShouldThrowAccessDenied_AndNeverDelete() {
        UserAccount owner = user("owner15@example.com");
        UserAccount stranger = user("stranger3@example.com");
        Garage garage = garage(owner, 50, 50, true, GarageStatus.ACTIVE);
        when(garageRepository.findById(garage.getId())).thenReturn(Optional.of(garage));

        assertThatThrownBy(() -> garageService.delete(garage.getId(), stranger))
                .isInstanceOf(AccessDeniedException.class);

        verify(garageRepository, never()).delete(any());
    }

    // ---------- listados y búsqueda ----------

    @Test
    void listActive_ShouldMapEachRepositoryResult() {
        UserAccount owner = user("owner16@example.com");
        Garage g1 = garage(owner, 10, 10, true, GarageStatus.ACTIVE);
        Garage g2 = garage(owner, 20, 20, true, GarageStatus.ACTIVE);
        when(garageRepository.findByStatus(GarageStatus.ACTIVE)).thenReturn(List.of(g1, g2));

        List<GarageResponse> result = garageService.listActive();

        assertThat(result).extracting(GarageResponse::id).containsExactlyInAnyOrder(g1.getId(), g2.getId());
    }

    @Test
    void listByCity_ShouldDelegateWithCityAndActiveStatus() {
        UserAccount owner = user("owner17@example.com");
        Garage garage = garage(owner, 10, 10, true, GarageStatus.ACTIVE);
        when(garageRepository.findByCityIgnoreCaseAndStatus("Bogotá", GarageStatus.ACTIVE))
                .thenReturn(List.of(garage));

        List<GarageResponse> result = garageService.listByCity("Bogotá");

        assertThat(result).hasSize(1);
        verify(garageRepository).findByCityIgnoreCaseAndStatus(eq("Bogotá"), eq(GarageStatus.ACTIVE));
    }

    @Test
    void listMine_ShouldDelegateWithOwnerId() {
        UserAccount owner = user("owner18@example.com");
        Garage garage = garage(owner, 10, 10, true, GarageStatus.ACTIVE);
        when(garageRepository.findByOwnerId(owner.getId())).thenReturn(List.of(garage));

        List<GarageResponse> result = garageService.listMine(owner);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).ownerId()).isEqualTo(owner.getId());
    }

    @Test
    void findNearby_ShouldDelegateExactParametersToRepository() {
        UserAccount owner = user("owner19@example.com");
        Garage garage = garage(owner, 10, 10, true, GarageStatus.ACTIVE);
        when(garageRepository.findNearby(4.710989, -74.072092, 5.0)).thenReturn(List.of(garage));

        List<GarageResponse> result = garageService.findNearby(4.710989, -74.072092, 5.0);

        assertThat(result).hasSize(1);
        verify(garageRepository).findNearby(4.710989, -74.072092, 5.0);
    }

    // ---------- helpers ----------

    private GarageRequest validRequest(boolean open24Hours, LocalTime openingTime, LocalTime closingTime) {
        return new GarageRequest(
                "Garaje Central", "Parqueadero cubierto", "3000000000",
                "Cra 7 # 1-01", "Bogotá", "Cundinamarca", "Colombia", "110111",
                4.710989, -74.072092,
                50, BigDecimal.valueOf(3500),
                open24Hours, openingTime, closingTime
        );
    }

    private Garage garage(UserAccount owner, int totalSpots, int availableSpots, boolean open24Hours, GarageStatus status) {
        Garage garage = new Garage();
        garage.setId(UUID.randomUUID());
        garage.setOwner(owner);
        garage.setName("Garaje Central");
        garage.setDescription("Parqueadero cubierto");
        garage.setPhone("3000000000");
        garage.setAddressLine("Cra 7 # 1-01");
        garage.setCity("Bogotá");
        garage.setState("Cundinamarca");
        garage.setCountry("Colombia");
        garage.setPostalCode("110111");
        garage.setLatitude(4.710989);
        garage.setLongitude(-74.072092);
        garage.setTotalSpots(totalSpots);
        garage.setAvailableSpots(availableSpots);
        garage.setPricePerHour(BigDecimal.valueOf(3500));
        garage.setOpen24Hours(open24Hours);
        if (!open24Hours) {
            garage.setOpeningTime(LocalTime.of(8, 0));
            garage.setClosingTime(LocalTime.of(20, 0));
        }
        garage.setStatus(status);
        return garage;
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
