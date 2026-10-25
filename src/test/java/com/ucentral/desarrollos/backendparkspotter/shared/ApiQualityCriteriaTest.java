package com.ucentral.desarrollos.backendparkspotter.shared;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RefreshTokenRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios de calidad de la API verificados de punta a punta (HTTP + seguridad + validación + base de datos):
 * códigos HTTP correctos, errores siempre en JSON con detalle por campo y en español, privacidad y CORS.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiQualityCriteriaTest {

    private static final String PASSWORD = "Password123!";

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired GarageRepository garageRepository;
    @Autowired GarageService garageService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    UserAccount owner;
    String ownerToken;

    @BeforeEach
    void setUp() {
        garageRepository.deleteAll();
        // El login exitoso crea refresh tokens que referencian al usuario: se borran primero.
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        roleRepository.deleteAll();
        Role role = new Role();
        role.setName("ROLE_USER");
        roleRepository.save(role);
        owner = user("calidad-owner@parkspotter.co", true);
        ownerToken = jwtService.generateAccessToken(owner.getId(), owner.getEmail(), List.of("ROLE_USER"));
    }

    @AfterEach
    void tearDown() {
        garageRepository.deleteAll();
        // El login exitoso crea refresh tokens que referencian al usuario: se borran primero.
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- contrato de errores ----------

    @Test
    void validationErrors_ListEveryInvalidField_InSpanish() throws Exception {
        String body = """
                {"name":"","phone":"abc","city":"Cali 2","totalSpots":0,"pricePerHour":-5,
                 "latitude":4.7,"longitude":-74.0,"open24Hours":false}
                """;

        authorized(post("/api/v1/garages").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Datos inválidos"))
                .andExpect(jsonPath("$.path").value("/api/v1/garages"))
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.phone").value(containsString("teléfono")))
                .andExpect(jsonPath("$.details.city").exists())
                .andExpect(jsonPath("$.details.addressLine").value(containsString("vacío")))
                .andExpect(jsonPath("$.details.totalSpots").exists())
                .andExpect(jsonPath("$.details.pricePerHour").exists())
                .andExpect(jsonPath("$.details.scheduleValid").exists());
    }

    @Test
    void malformedJson_Returns400WithClearMessage() throws Exception {
        authorized(post("/api/v1/garages").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cuerpo de la petición no es un JSON válido"));
    }

    @Test
    void wrongJsonType_PointsToTheField() throws Exception {
        authorized(post("/api/v1/garages").contentType(MediaType.APPLICATION_JSON).content("{\"totalSpots\":\"muchas\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.totalSpots").exists());
    }

    @Test
    void missingOpen24Hours_IsAValidationErrorNotAParseError() throws Exception {
        String body = """
                {"name":"Garaje Norte","addressLine":"Calle 100 # 15-20","city":"Bogotá","state":"Cundinamarca",
                 "country":"Colombia","latitude":4.68,"longitude":-74.05,"totalSpots":10,"pricePerHour":3000}
                """;

        authorized(post("/api/v1/garages").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.open24Hours").exists());
    }

    @Test
    void unknownRoute_Returns404Json() throws Exception {
        authorized(get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Recurso no encontrado"));
    }

    @Test
    void wrongHttpMethod_Returns405() throws Exception {
        authorized(delete("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void wrongContentType_Returns415() throws Exception {
        authorized(post("/api/v1/garages").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void invalidQueryParams_AreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/garages/nearby").param("lat", "4.7").param("lng", "-74").param("radiusKm", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.radiusKm").exists());
        mockMvc.perform(get("/api/v1/garages/nearby").param("lat", "4.7").param("lng", "-74").param("radiusKm", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/garages/nearby").param("lat", "100").param("lng", "-74"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.lat").exists());
        mockMvc.perform(get("/api/v1/garages/search").param("page", "20000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.page").exists());
        mockMvc.perform(get("/api/v1/garages").param("city", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    // ---------- autenticación ----------

    @Test
    void register_WithWeakPasswordOrInvalidEmail_Returns400PerField() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b\",\"password\":\"corta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.password").value(containsString("12 y 72")));
    }

    @Test
    void register_WithDuplicatedEmail_Returns409() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"CALIDAD-OWNER@parkspotter.co\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El correo ya está registrado"));
    }

    @Test
    void login_WithWrongPassword_Returns401_AndWithValidOne_Returns200() throws Exception {
        login(owner.getEmail(), "OtraPassword999", "web-app").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
        login(owner.getEmail(), PASSWORD, "web-app").andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void login_OfDisabledUser_Returns401WithGenericMessage() throws Exception {
        UserAccount disabled = user("deshabilitado@parkspotter.co", false);

        login(disabled.getEmail(), PASSWORD, "web-app")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales inválidas"));
    }

    @Test
    void login_AfterTooManyFailures_Returns429() throws Exception {
        String clientId = "rate-test-" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 5; i++) {
            login(owner.getEmail(), "Incorrecta12345", clientId).andExpect(status().isUnauthorized());
        }

        login(owner.getEmail(), PASSWORD, clientId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    void login_WithInvalidClientIdHeader_Returns400() throws Exception {
        login(owner.getEmail(), PASSWORD, "x".repeat(65)).andExpect(status().isBadRequest());
        login(owner.getEmail(), PASSWORD, "cliente con espacios").andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_ToTheSameOne_Returns400() throws Exception {
        authorized(patch("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.newPasswordDifferentFromCurrent").exists());
    }

    @Test
    void changePassword_WithWrongCurrent_Returns400_NotLoggingTheUserOut() throws Exception {
        authorized(patch("/api/v1/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"NoEsLaActual1\",\"newPassword\":\"NuevaPassword2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual no es correcta"));
    }

    // ---------- privacidad y normalización ----------

    @Test
    void publicGarageEndpoints_DoNotExposeOwnerEmail_ButMineDoes() throws Exception {
        GarageResponse garage = garageService.create(garageRequest("  Medellín  "), owner);

        mockMvc.perform(get("/api/v1/garages/" + garage.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerEmail").isEmpty())
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        mockMvc.perform(get("/api/v1/garages"))
                .andExpect(content().string(not(containsString(owner.getEmail()))));
        authorized(get("/api/v1/garages/mine"))
                .andExpect(jsonPath("$[0].ownerEmail").value(owner.getEmail()));
    }

    @Test
    void textFields_AreTrimmed_SoFiltersWork() throws Exception {
        GarageResponse garage = garageService.create(garageRequest("  Medellín  "), owner);

        mockMvc.perform(get("/api/v1/garages/" + garage.id()))
                .andExpect(jsonPath("$.city").value("Medellín"));
        mockMvc.perform(get("/api/v1/garages/search").param("city", "medellín"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void updatingAvailableSpotsDirectly_Returns400WithFieldDetail() throws Exception {
        GarageResponse garage = garageService.create(garageRequest("Bogotá"), owner);

        authorized(put("/api/v1/garages/" + garage.id()).contentType(MediaType.APPLICATION_JSON).content("{\"availableSpots\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.availableSpots").value(containsString("plazas")));
    }

    // ---------- CORS (front web) ----------

    @Test
    void corsPreflight_AllowsClientIdHeaderUsedByLogin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-client-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsStringIgnoringCase("X-Client-Id")));
    }

    @Test
    void corsPreflight_FromUnknownOrigin_IsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private ResultActions authorized(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken));
    }

    private ResultActions login(String email, String password, String clientId) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .header("X-Client-Id", clientId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private UserAccount user(String email, boolean enabled) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setEnabled(enabled);
        user.getRoles().add(roleRepository.findByName("ROLE_USER").orElseThrow());
        return userRepository.save(user);
    }

    private static GarageRequest garageRequest(String city) {
        return new GarageRequest("Garaje Calidad", "Pruebas", "3001234567",
                "Calle 10 # 5-20", city, "Antioquia", "Colombia", "050001",
                6.2442, -75.5812, 5, BigDecimal.valueOf(3000), true, null, null);
    }
}
