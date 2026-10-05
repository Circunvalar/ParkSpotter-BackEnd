package com.ucentral.desarrollos.backendparkspotter.garageManagement;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageUpdateRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.config.location=classpath:/application.properties")
class GarageServiceTest {

    @Autowired GarageService garageService;
    @Autowired GarageRepository garageRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void clean() {
        garageRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        roleRepository.save(role("ROLE_USER"));
        roleRepository.save(role("ROLE_ADMIN"));
    }

    @Test
    void createShouldPersistGarageWithOwnerAndDefaults() {
        UserAccount owner = saveUser("owner@example.com");

        GarageResponse response = garageService.create(validRequest(), owner);

        assertThat(response.id()).isNotNull();
        assertThat(response.ownerId()).isEqualTo(owner.getId());
        assertThat(response.ownerEmail()).isEqualTo(owner.getEmail());
        assertThat(response.availableSpots()).isEqualTo(response.totalSpots());
        assertThat(response.status()).isEqualTo(GarageStatus.ACTIVE);
        assertThat(response.latitude()).isEqualTo(4.710989);
        assertThat(response.longitude()).isEqualTo(-74.072092);
    }

    @Test
    void createWithoutScheduleAndNot24HoursShouldFail() {
        UserAccount owner = saveUser("owner2@example.com");
        GarageRequest request = new GarageRequest(
                "Garaje sin horario", "desc", "3000000000",
                "Cra 7 # 1-01", "Bogotá", "Cundinamarca", "Colombia", "110111",
                4.710989, -74.072092,
                20, BigDecimal.valueOf(3500),
                false, null, null
        );

        assertThatThrownBy(() -> garageService.create(request, owner))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void updateByOwnerShouldChangeFields() {
        UserAccount owner = saveUser("owner3@example.com");
        GarageResponse created = garageService.create(validRequest(), owner);

        GarageUpdateRequest update = new GarageUpdateRequest(
                "Nuevo nombre", null, null,
                null, null, null, null, null,
                null, null,
                null, null, BigDecimal.valueOf(5000),
                null, null, null
        );

        GarageResponse updated = garageService.update(created.id(), update, owner);

        assertThat(updated.name()).isEqualTo("Nuevo nombre");
        assertThat(updated.pricePerHour()).isEqualByComparingTo(BigDecimal.valueOf(5000));
        // Campos no enviados se conservan
        assertThat(updated.city()).isEqualTo(created.city());
    }

    @Test
    void updateByNonOwnerShouldThrowAccessDenied() {
        UserAccount owner = saveUser("owner4@example.com");
        UserAccount stranger = saveUser("stranger@example.com");
        GarageResponse created = garageService.create(validRequest(), owner);

        GarageUpdateRequest update = new GarageUpdateRequest(
                "Hackeado", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> garageService.update(created.id(), update, stranger))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateByAdminShouldSucceedEvenWithoutOwnership() {
        UserAccount owner = saveUser("owner5@example.com");
        UserAccount admin = saveUser("admin@example.com");
        admin.getRoles().add(roleRepository.findByName("ROLE_ADMIN").orElseThrow());
        userRepository.save(admin);

        GarageResponse created = garageService.create(validRequest(), owner);

        GarageUpdateRequest update = new GarageUpdateRequest(
                "Editado por admin", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );

        GarageResponse updated = garageService.update(created.id(), update, admin);

        assertThat(updated.name()).isEqualTo("Editado por admin");
    }

    @Test
    void availableSpotsGreaterThanTotalShouldFail() {
        UserAccount owner = saveUser("owner6@example.com");
        GarageResponse created = garageService.create(validRequest(), owner);

        GarageUpdateRequest update = new GarageUpdateRequest(
                null, null, null, null, null, null, null, null,
                null, null, null, created.totalSpots() + 5, null, null, null, null
        );

        assertThatThrownBy(() -> garageService.update(created.id(), update, owner))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void deleteShouldRemoveGarage() {
        UserAccount owner = saveUser("owner7@example.com");
        GarageResponse created = garageService.create(validRequest(), owner);

        garageService.delete(created.id(), owner);

        assertThatThrownBy(() -> garageService.getById(created.id()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getByIdNotFoundShouldThrow() {
        assertThatThrownBy(() -> garageService.getById(UUID.randomUUID()))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listActiveShouldExcludeInactiveGarages() {
        UserAccount owner = saveUser("owner8@example.com");
        GarageResponse active = garageService.create(validRequest(), owner);
        GarageResponse toDeactivate = garageService.create(validRequest(), owner);
        garageService.changeStatus(toDeactivate.id(), GarageStatus.INACTIVE, owner);

        var activeList = garageService.listActive();

        assertThat(activeList).extracting(GarageResponse::id).contains(active.id());
        assertThat(activeList).extracting(GarageResponse::id).doesNotContain(toDeactivate.id());
    }

    @Test
    void findNearbyShouldReturnGarageWithinRadiusAndExcludeFarOnes() {
        UserAccount owner = saveUser("owner9@example.com");
        // Garaje en Bogotá (centro)
        GarageResponse bogota = garageService.create(validRequest(), owner);
        // Garaje en Medellín (~240 km de Bogotá)
        GarageRequest medellinRequest = new GarageRequest(
                "Garaje Medellín", "desc", "3000000000",
                "Cra 50 # 10-10", "Medellín", "Antioquia", "Colombia", "050001",
                6.244203, -75.581212,
                10, BigDecimal.valueOf(4000),
                true, null, null
        );
        GarageResponse medellin = garageService.create(medellinRequest, owner);

        var nearby = garageService.findNearby(4.710989, -74.072092, 10);

        assertThat(nearby).extracting(GarageResponse::id).contains(bogota.id());
        assertThat(nearby).extracting(GarageResponse::id).doesNotContain(medellin.id());
    }

    private GarageRequest validRequest() {
        return new GarageRequest(
                "Garaje Central", "Parqueadero cubierto", "3000000000",
                "Cra 7 # 1-01", "Bogotá", "Cundinamarca", "Colombia", "110111",
                4.710989, -74.072092,
                50, BigDecimal.valueOf(3500),
                true, null, null
        );
    }

    private UserAccount saveUser(String email) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("Password123!"));
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByName("ROLE_USER").orElseThrow());
        return userRepository.save(user);
    }

    private Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }
}
