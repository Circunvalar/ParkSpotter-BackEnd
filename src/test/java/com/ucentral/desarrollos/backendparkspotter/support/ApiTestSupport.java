package com.ucentral.desarrollos.backendparkspotter.support;

import com.jayway.jsonpath.JsonPath;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.RoleNames;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de las pruebas de API de los Sprints 04 a 06: base H2 limpia en cada prueba, usuarios con roles,
 * tokens y garajes de ejemplo. Comparte el contexto de Spring con las demás pruebas @SpringBootTest + MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "Password123!";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;
    @Autowired protected GarageService garageService;
    @Autowired protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanBefore() {
        truncateAll();
        for (String name : List.of(RoleNames.USER, RoleNames.DRIVER, RoleNames.OWNER, RoleNames.ADMIN)) {
            Role role = new Role();
            role.setName(name);
            roleRepository.save(role);
        }
    }

    @AfterEach
    void cleanAfter() {
        truncateAll();
    }

    /** Vacía todas las tablas (H2): más simple y robusto que borrar en orden respetando las llaves foráneas. */
    protected void truncateAll() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
                String.class);
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        tables.forEach(table -> jdbcTemplate.execute("TRUNCATE TABLE \"" + table + "\""));
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");
    }

    // ---------- usuarios y tokens ----------

    protected UserAccount user(String email, String... roles) {
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setFullName("Usuario " + email.substring(0, email.indexOf('@')));
        user.getRoles().add(roleRepository.findByName(RoleNames.USER).orElseThrow());
        Arrays.stream(roles).forEach(name -> user.getRoles().add(roleRepository.findByName(name).orElseThrow()));
        return userRepository.save(user);
    }

    protected UserAccount driver(String email) {
        return user(email, RoleNames.DRIVER);
    }

    protected UserAccount owner(String email) {
        return user(email, RoleNames.OWNER);
    }

    protected UserAccount admin(String email) {
        return user(email, RoleNames.ADMIN);
    }

    protected String bearer(UserAccount user) {
        return "Bearer " + jwtService.generateAccessToken(user.getId(), user.getEmail(), List.of());
    }

    protected ResultActions perform(AbstractMockHttpServletRequestBuilder<?> request, UserAccount user) throws Exception {
        return mockMvc.perform(request.header("Authorization", bearer(user)));
    }

    protected ResultActions performJson(MockHttpServletRequestBuilder request, UserAccount user, String body) throws Exception {
        if (user != null) {
            request.header("Authorization", bearer(user));
        }
        return mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    protected static String read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path).toString();
    }

    // ---------- garajes ----------

    protected GarageResponse garage24h(UserAccount owner, String name, int spots) {
        return garageService.create(garageRequest(name, spots, true, null, null), owner);
    }

    protected GarageResponse garageWithSchedule(UserAccount owner, String name, LocalTime opening, LocalTime closing) {
        return garageService.create(garageRequest(name, 2, false, opening, closing), owner);
    }

    protected static GarageRequest garageRequest(String name, int spots, boolean open24Hours, LocalTime opening, LocalTime closing) {
        return new GarageRequest(name, "Parqueadero de prueba", "3001234567", "Calle 10 # 5-20", "Bogotá",
                "Cundinamarca", "Colombia", "110111", 4.711, -74.072, spots, BigDecimal.valueOf(4000),
                open24Hours, opening, closing);
    }

    // ---------- reservas ----------

    /** Crea una reserva de carro por HTTP y devuelve su id. */
    protected String reserveId(UserAccount driver, UUID garageId, long startInMinutes, long endInMinutes) throws Exception {
        String body = "{\"garageId\":\"" + garageId + "\",\"vehicleType\":\"CAR\",\"vehiclePlate\":\"ABC123\","
                + "\"startTime\":\"" + inMinutes(startInMinutes) + "\",\"endTime\":\"" + inMinutes(endInMinutes) + "\"}";
        return read(performJson(post("/api/v1/reservations"), driver, body)
                .andExpect(status().isCreated()), "$.id");
    }

    /** Crea el pago sandbox de la reserva y lo aprueba; devuelve el id del pago. */
    protected String payApproved(UserAccount driver, String reservationId) throws Exception {
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");
        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"),
                driver, "{\"status\":\"APPROVED\"}");
        return paymentId;
    }

    /** Reserva que empieza ya, pagada, con entrada y salida registradas (estado COMPLETED). */
    protected String completedReservation(UserAccount driver, UserAccount owner, UUID garageId) throws Exception {
        String id = reserveId(driver, garageId, 1, 61);
        payApproved(driver, id);
        String code = read(perform(get("/api/v1/reservations/" + id), driver), "$.code");
        performJson(post("/api/v1/reservations/check-in"), owner, "{\"code\":\"" + code + "\"}");
        performJson(post("/api/v1/reservations/check-out"), owner, "{\"code\":\"" + code + "\"}")
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        return id;
    }

    /** Instante futuro redondeado al minuto (los fronts envían horas sin segundos). */
    protected static Instant inMinutes(long minutes) {
        return Instant.now().plus(minutes, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
    }
}
