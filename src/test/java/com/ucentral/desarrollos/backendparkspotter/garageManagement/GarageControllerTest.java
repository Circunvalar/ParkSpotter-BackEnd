package com.ucentral.desarrollos.backendparkspotter.garageManagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@TestPropertySource(locations = "classpath:application.properties")
class GarageControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired GarageRepository garageRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;
    @Autowired GarageService garageService;

    @BeforeEach
    void clean() {
        garageRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        roleRepository.save(role("ROLE_USER"));
    }

    @Test
    void listGaragesIsPublicEvenWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/garages"))
                .andExpect(status().isOk());
    }

    @Test
    void createWithoutTokenShouldBeUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/garages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createWithTokenShouldPersistAndReturnGarage() throws Exception {
        UserAccount owner = user("garageowner@example.com");
        String token = tokenFor(owner);

        mockMvc.perform(post("/api/v1/garages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Garaje Central"))
                .andExpect(jsonPath("$.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.availableSpots").value(50))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createWithInvalidBodyShouldReturnBadRequest() throws Exception {
        UserAccount owner = user("invalidbody@example.com");
        String token = tokenFor(owner);

        String invalidBody = """
                {"name":"","totalSpots":-1}
                """;

        mockMvc.perform(post("/api/v1/garages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mineEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/garages/mine"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mineEndpointReturnsOnlyOwnGarages() throws Exception {
        UserAccount owner = user("mineowner@example.com");
        String token = tokenFor(owner);
        garageService.create(validRequest(), owner);

        mockMvc.perform(get("/api/v1/garages/mine")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void getUnknownGarageShouldReturnNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateByNonOwnerShouldBeForbidden() throws Exception {
        UserAccount owner = user("realowner@example.com");
        UserAccount stranger = user("intruder@example.com");
        String strangerToken = tokenFor(stranger);
        GarageResponse created = garageService.create(validRequest(), owner);

        String updateBody = """
                {"name":"Intento de hackeo"}
                """;

        mockMvc.perform(put("/api/v1/garages/" + created.id())
                        .header("Authorization", "Bearer " + strangerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteByOwnerShouldReturnNoContent() throws Exception {
        UserAccount owner = user("deleteowner@example.com");
        String token = tokenFor(owner);
        GarageResponse created = garageService.create(validRequest(), owner);

        mockMvc.perform(delete("/api/v1/garages/" + created.id())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
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

    private String tokenFor(UserAccount user) {
        return jwtService.generateAccessToken(user.getId(), user.getEmail(), List.of("ROLE_USER"));
    }

    private UserAccount user(String email) {
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
