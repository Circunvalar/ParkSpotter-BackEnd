package com.ucentral.desarrollos.backendparkspotter.garageManagement;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.ParkingSpotRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints de gestión de garajes y plazas por HTTP, con permisos de dueño, extraño y administrador.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GarageManagementApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired GarageService garageService;
    @Autowired ParkingSpotService parkingSpotService;
    @Autowired GarageRepository garageRepository;
    @Autowired ParkingSpotRepository spotRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtService jwtService;

    UserAccount owner;
    UserAccount stranger;
    UserAccount admin;
    GarageResponse garage;

    @BeforeEach
    void setUp() {
        cleanDatabase();
        roleRepository.save(role("ROLE_USER"));
        roleRepository.save(role("ROLE_ADMIN"));
        owner = user("dueno@parkspotter.co", "ROLE_USER");
        stranger = user("extrano@parkspotter.co", "ROLE_USER");
        admin = user("admin@parkspotter.co", "ROLE_ADMIN");
        garage = garageService.create(request("Garaje Gestión", "Bogotá", 4.710989, -74.072092), owner);
    }

    @AfterEach
    void cleanDatabase() {
        garageRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
    }

    // ---------- PUT /garages/{id} ----------

    @Test
    void update_ByOwner_ChangesFieldsTrimsTextAndCapacity() throws Exception {
        String body = """
                {"name":"  Garaje Renovado  ","description":"","phone":"+57 601 234 5678","addressLine":"Calle 26 # 13-19",
                 "city":"Bogot\\u00e1","state":"Cundinamarca","country":"Colombia","postalCode":"110311",
                 "latitude":4.6150,"longitude":-74.0700,"totalSpots":8,"pricePerHour":4200.50,
                 "open24Hours":false,"openingTime":"07:00","closingTime":"21:00"}
                """;

        mockMvc.perform(put("/api/v1/garages/" + garage.id()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Garaje Renovado"))
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.totalSpots").value(8))
                .andExpect(jsonPath("$.availableSpots").value(8))
                .andExpect(jsonPath("$.pricePerHour").value(4200.5))
                .andExpect(jsonPath("$.open24Hours").value(false))
                .andExpect(jsonPath("$.openingTime").value("07:00:00"))
                .andExpect(jsonPath("$.ownerEmail").value(owner.getEmail()));

        assertThat(spotRepository.findByGarageFiltered(garage.id(), null, null)).hasSize(8);
    }

    @Test
    void update_SwitchingOff24HoursWithoutSchedule_Returns400() throws Exception {
        mockMvc.perform(put("/api/v1/garages/" + garage.id()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"open24Hours\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("openingTime")));
    }

    @Test
    void update_ByStranger_Returns403_ByAdmin_Returns200() throws Exception {
        mockMvc.perform(put("/api/v1/garages/" + garage.id()).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Robado\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/garages/" + garage.id()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Corregido por admin\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Corregido por admin"));
    }

    @Test
    void update_ReducingCapacityBelowOccupiedSpots_Returns400() throws Exception {
        parkingSpotService.list(garage.id(), null, null).forEach(spot ->
                parkingSpotService.changeStatus(garage.id(), spot.id(), SpotStatus.OCCUPIED, owner));

        mockMvc.perform(put("/api/v1/garages/" + garage.id()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"totalSpots\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ocupadas")));
    }

    // ---------- PATCH /garages/{id}/status ----------

    @Test
    void changeStatus_ToInactive_HidesGarageFromSearchAndMarksItClosed() throws Exception {
        mockMvc.perform(patch("/api/v1/garages/" + garage.id() + "/status").param("status", "INACTIVE")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.availability").value("CLOSED"));

        mockMvc.perform(get("/api/v1/garages/search"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.availability").value("CLOSED"));
    }

    @Test
    void changeStatus_ByStranger_Returns403_AndInvalidValue_Returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/garages/" + garage.id() + "/status").param("status", "INACTIVE")
                        .header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/garages/" + garage.id() + "/status").param("status", "BORRADO")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/v1/garages/" + garage.id() + "/status").header("Authorization", bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.status").exists());
    }

    // ---------- GET /garages y /nearby ----------

    @Test
    void list_ByCityOrAll_IsPublic() throws Exception {
        garageService.create(request("Garaje Paisa", "Medellín", 6.2442, -75.5812), owner);

        mockMvc.perform(get("/api/v1/garages").param("city", "medellín"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Garaje Paisa"));
        mockMvc.perform(get("/api/v1/garages").param("city", "  "))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void nearby_ReturnsGaragesWithinRadius_WithoutOwnerEmail() throws Exception {
        garageService.create(request("Garaje Paisa", "Medellín", 6.2442, -75.5812), owner);

        mockMvc.perform(get("/api/v1/garages/nearby").param("lat", "4.711").param("lng", "-74.072").param("radiusKm", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(garage.id().toString()))
                .andExpect(jsonPath("$[0].ownerEmail").isEmpty());
    }

    // ---------- PUT /garages/{id}/spots/{spotId} ----------

    @Test
    void updateSpot_ByOwner_ChangesCodeFloorAndType() throws Exception {
        ParkingSpotResponse spot = parkingSpotService.list(garage.id(), null, null).getFirst();

        mockMvc.perform(put("/api/v1/garages/" + garage.id() + "/spots/" + spot.id()).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"e-01\",\"floor\":-1,\"vehicleType\":\"ELECTRIC\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("E-01"))
                .andExpect(jsonPath("$.floor").value(-1))
                .andExpect(jsonPath("$.vehicleType").value("ELECTRIC"));

        mockMvc.perform(get("/api/v1/garages/search").param("vehicleType", "ELECTRIC"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updateSpot_WithCodeOfAnotherSpot_Returns400_AndByStranger_Returns403() throws Exception {
        List<ParkingSpotResponse> spots = parkingSpotService.list(garage.id(), null, null);
        String url = "/api/v1/garages/" + garage.id() + "/spots/" + spots.get(0).id();

        mockMvc.perform(put(url).header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + spots.get(1).code() + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(url).header("Authorization", bearer(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"floor\":3}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void spotEndpoints_OnUnknownGarageOrSpot_Return404() throws Exception {
        mockMvc.perform(patch("/api/v1/garages/" + UUID.randomUUID() + "/spots/" + UUID.randomUUID() + "/status")
                        .param("status", "OCCUPIED").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/garages/" + garage.id() + "/spots/" + UUID.randomUUID() + "/status")
                        .param("status", "OCCUPIED").header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/garages/" + UUID.randomUUID() + "/spots"))
                .andExpect(status().isNotFound());
    }

    // ---------- DELETE /garages/{id} ----------

    @Test
    void delete_RemovesGarageAndItsSpots() throws Exception {
        mockMvc.perform(delete("/api/v1/garages/" + garage.id()).header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/garages/" + garage.id()).header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());

        assertThat(spotRepository.findByGarageFiltered(garage.id(), null, null)).isEmpty();
        mockMvc.perform(get("/api/v1/garages/" + garage.id())).andExpect(status().isNotFound());
    }

    // ---------- helpers ----------

    private String bearer(UserAccount user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).toList();
        return "Bearer " + jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
    }

    private UserAccount user(String email, String roleName) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash("$2a$12$hashdepruebahashdepruebahashdepruebahashdepruebahashde");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        return userRepository.save(user);
    }

    private static Role role(String name) {
        Role role = new Role();
        role.setName(name);
        return role;
    }

    private static GarageRequest request(String name, String city, double lat, double lng) {
        return new GarageRequest(name, "Parqueadero de prueba", "3001234567", "Calle 10 # 5-20", city,
                "Cundinamarca", "Colombia", "110111", lat, lng, 3, BigDecimal.valueOf(3000), true,
                null, null);
    }
}
