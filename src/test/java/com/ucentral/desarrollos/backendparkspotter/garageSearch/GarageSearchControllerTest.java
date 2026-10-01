package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.ParkingSpotResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static com.ucentral.desarrollos.backendparkspotter.garageSearch.GarageSearchTestData.CENTER_LAT;
import static com.ucentral.desarrollos.backendparkspotter.garageSearch.GarageSearchTestData.CENTER_LNG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la API de búsqueda tal como la consumen los fronts web y Android.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(GarageSearchTestData.FixedClockConfig.class)
class GarageSearchControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired GarageService garageService;
    @Autowired ParkingSpotService parkingSpotService;
    @Autowired GarageRepository garageRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired JwtService jwtService;

    UserAccount owner;
    GarageResponse centro;
    GarageResponse nocturno;

    @BeforeEach
    void setUp() {
        GarageSearchTestData.cleanDatabase(garageRepository, userRepository, roleRepository);
        owner = GarageSearchTestData.user(userRepository, roleRepository, "api-owner@example.com");
        centro = GarageSearchTestData.centro(garageService, owner);
        nocturno = GarageSearchTestData.nocturnoUsaquen(garageService, owner);
    }

    @AfterEach
    void tearDown() {
        garageRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ---------- búsqueda ----------

    @Test
    void search_IsPublicAndReturnsPageFormat() throws Exception {
        mockMvc.perform(get("/api/v1/garages/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.content[0].name").value("Garaje Centro"))
                .andExpect(jsonPath("$.content[0].availability").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].availableSpots").value(50))
                .andExpect(jsonPath("$.content[0].openNow").value(true));
    }

    @Test
    void search_WithInvalidOrExpiredToken_StillWorksForPublicEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/garages/search").header("Authorization", "Bearer token-invalido"))
                .andExpect(status().isOk());
    }

    @Test
    void search_WithLocation_ReturnsDistanceSortedResults() throws Exception {
        mockMvc.perform(get("/api/v1/garages/search")
                        .param("lat", String.valueOf(CENTER_LAT))
                        .param("lng", String.valueOf(CENTER_LNG))
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Garaje Centro"))
                .andExpect(jsonPath("$.content[0].distanceKm").value(0.0))
                .andExpect(jsonPath("$.content[1].name").value("Garaje Nocturno Usaquén"));
    }

    @Test
    void search_WithFilters_FromQueryParams() throws Exception {
        mockMvc.perform(get("/api/v1/garages/search")
                        .param("q", "usaquén")
                        .param("maxPrice", "3000")
                        .param("vehicleType", "CAR")
                        .param("onlyAvailable", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(nocturno.id().toString()))
                .andExpect(jsonPath("$.content[0].openNow").value(false));
    }

    @Test
    void search_WithPageSizeOverLimit_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/garages/search").param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_WithUnknownEnumValue_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/garages/search").param("vehicleType", "AVION"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/garages/search").param("sort", "RANDOM"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_WithLatButWithoutLng_ReturnsBadRequestWithMessage() throws Exception {
        mockMvc.perform(get("/api/v1/garages/search").param("lat", "4.7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lat y lng deben enviarse juntos"));
    }

    // ---------- mapa ----------

    @Test
    void map_IsPublicAndReturnsMarkers() throws Exception {
        mockMvc.perform(get("/api/v1/garages/map")
                        .param("minLat", "4.55").param("minLng", "-74.20")
                        .param("maxLat", "4.80").param("maxLng", "-73.95"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.markers[0].latitude").exists())
                .andExpect(jsonPath("$.markers[0].availability").exists());
    }

    @Test
    void map_WithoutBounds_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/garages/map"))
                .andExpect(status().isBadRequest());
    }

    // ---------- detalle y disponibilidad ----------

    @Test
    void detail_IsPublicAndCanIncludeSpots() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + nocturno.id() + "/detail").param("includeSpots", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Garaje Nocturno Usaquén"))
                .andExpect(jsonPath("$.availability.totalSpots").value(10))
                .andExpect(jsonPath("$.availability.openNow").value(false))
                .andExpect(jsonPath("$.availabilityByVehicleType[0].vehicleType").value("CAR"))
                .andExpect(jsonPath("$.floors[0]").value(1))
                .andExpect(jsonPath("$.spots.length()").value(10))
                .andExpect(jsonPath("$.spots[0].code").value("P-001"))
                .andExpect(jsonPath("$.spots[0].status").value("AVAILABLE"));
    }

    @Test
    void detail_UnknownGarage_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + UUID.randomUUID() + "/detail"))
                .andExpect(status().isNotFound());
    }

    @Test
    void detail_WithMalformedId_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/garages/no-es-un-uuid/detail"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void availability_IsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + centro.id() + "/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.garageId").value(centro.id().toString()))
                .andExpect(jsonPath("$.availableSpots").value(50));
    }

    // ---------- plazas y estado en tiempo real ----------

    @Test
    void spotsList_IsPublicAndFilterable() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + nocturno.id() + "/spots").param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10));
    }

    @Test
    void changeSpotStatus_WithoutToken_IsUnauthorized() throws Exception {
        ParkingSpotResponse spot = firstSpot(nocturno.id());

        mockMvc.perform(patch("/api/v1/garages/" + nocturno.id() + "/spots/" + spot.id() + "/status")
                        .param("status", "OCCUPIED"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changeSpotStatus_ByStranger_IsForbidden() throws Exception {
        UserAccount stranger = GarageSearchTestData.user(userRepository, roleRepository, "stranger-api@example.com");
        ParkingSpotResponse spot = firstSpot(nocturno.id());

        mockMvc.perform(patch("/api/v1/garages/" + nocturno.id() + "/spots/" + spot.id() + "/status")
                        .header("Authorization", "Bearer " + tokenFor(stranger))
                        .param("status", "OCCUPIED"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeSpotStatus_ByOwner_UpdatesAvailabilitySeenBySearch() throws Exception {
        ParkingSpotResponse spot = firstSpot(nocturno.id());

        mockMvc.perform(patch("/api/v1/garages/" + nocturno.id() + "/spots/" + spot.id() + "/status")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .param("status", "RESERVED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));

        mockMvc.perform(get("/api/v1/garages/" + nocturno.id() + "/availability"))
                .andExpect(jsonPath("$.availableSpots").value(9))
                .andExpect(jsonPath("$.reservedSpots").value(1));
    }

    @Test
    void addAndDeleteSpot_ByOwner() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/garages/" + nocturno.id() + "/spots")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"M-01","floor":2,"vehicleType":"MOTORCYCLE"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("M-01"))
                .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"))
                .andReturn();
        String spotId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/garages/" + nocturno.id() + "/availability"))
                .andExpect(jsonPath("$.totalSpots").value(11));

        mockMvc.perform(delete("/api/v1/garages/" + nocturno.id() + "/spots/" + spotId)
                        .header("Authorization", "Bearer " + tokenFor(owner)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/garages/" + nocturno.id() + "/availability"))
                .andExpect(jsonPath("$.totalSpots").value(10));
    }

    @Test
    void addSpot_WithInvalidCode_ReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/garages/" + nocturno.id() + "/spots")
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"<script>"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateGarageAvailableSpotsDirectly_IsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/garages/" + centro.id())
                        .header("Authorization", "Bearer " + tokenFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"availableSpots":10}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void availabilityStream_IsPublicAndSendsCurrentStateImmediately() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/garages/" + centro.id() + "/availability/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:availability");
        assertThat(body).contains("\"garageId\":\"" + centro.id() + "\"");
        assertThat(body).contains("\"availableSpots\":50");
    }

    @Test
    void availabilityStream_PushesChangesAfterSpotStatusChange() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/garages/" + centro.id() + "/availability/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted())
                .andReturn();

        ParkingSpotResponse spot = firstSpot(centro.id());
        parkingSpotService.changeStatus(centro.id(), spot.id(), SpotStatus.OCCUPIED, owner);

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"availableSpots\":49");
        assertThat(body).contains("\"occupiedSpots\":1");
    }

    @Test
    void globalStream_IsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/garages/availability/stream").accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(request().asyncStarted());
    }

    @Test
    void availabilityStream_ForUnknownGarage_ReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + UUID.randomUUID() + "/availability/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    // ---------- endpoints protegidos siguen protegidos ----------

    @Test
    void mineEndpoint_StillRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/garages/mine"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private ParkingSpotResponse firstSpot(UUID garageId) {
        List<ParkingSpotResponse> spots = parkingSpotService.list(garageId, SpotStatus.AVAILABLE, null);
        return spots.getFirst();
    }

    private String tokenFor(UserAccount user) {
        return jwtService.generateAccessToken(user.getId(), user.getEmail(), List.of("ROLE_USER"));
    }
}
