package com.ucentral.desarrollos.backendparkspotter.review;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Calificaciones (Sprint 06, T6-01). */
class ReviewApiTest extends ApiTestSupport {

    UserAccount owner;
    UserAccount driver;
    UserAccount otherDriver;
    UserAccount admin;
    GarageResponse garage;

    @BeforeEach
    void setUp() {
        owner = owner("dueno-resenas@parkspotter.co");
        driver = driver("conductor-resenas@parkspotter.co");
        otherDriver = driver("otro-resenas@parkspotter.co");
        admin = admin("admin-resenas@parkspotter.co");
        garage = garage24h(owner, "Garaje Reseñas", 3);
    }

    @Test
    void create_UpdatesAverage_AndListsPublicly() throws Exception {
        String first = completedReservation(driver, owner, garage.id());
        String second = completedReservation(otherDriver, owner, garage.id());

        performJson(post("/api/v1/reservations/" + first + "/review"), driver, "{\"rating\":5,\"comment\":\"  Excelente  \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value("Excelente"))
                .andExpect(jsonPath("$.authorName").value("Usuario"));
        performJson(post("/api/v1/reservations/" + second + "/review"), otherDriver, "{\"rating\":4,\"comment\":\" \"}")
                .andExpect(jsonPath("$.comment").doesNotExist());

        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));
        mockMvc.perform(get("/api/v1/garages/" + garage.id()))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.ratingCount").value(2));
        mockMvc.perform(get("/api/v1/garages/search").param("minRating", "5")).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get("/api/v1/garages/search").param("minRating", "4").param("sort", "RATING"))
                .andExpect(jsonPath("$.content", hasSize(1)));
        perform(get("/api/v1/notifications"), owner).andExpect(jsonPath("$.content[0].type").value("NEW_REVIEW"));
        mockMvc.perform(get("/api/v1/garages/" + UUID.randomUUID() + "/reviews")).andExpect(status().isNotFound());
    }

    @Test
    void create_Rejects_Duplicate_NotCompleted_ForeignAndUnknown() throws Exception {
        String completed = completedReservation(driver, owner, garage.id());
        performJson(post("/api/v1/reservations/" + completed + "/review"), otherDriver, "{\"rating\":3}").andExpect(status().isForbidden());
        performJson(post("/api/v1/reservations/" + completed + "/review"), driver, "{\"rating\":3}").andExpect(status().isCreated());
        performJson(post("/api/v1/reservations/" + completed + "/review"), driver, "{\"rating\":3}").andExpect(status().isConflict());

        String pending = reserveId(driver, garage.id(), 120, 180);
        performJson(post("/api/v1/reservations/" + pending + "/review"), driver, "{\"rating\":3}").andExpect(status().isConflict());
        performJson(post("/api/v1/reservations/" + UUID.randomUUID() + "/review"), driver, "{\"rating\":3}").andExpect(status().isNotFound());
        performJson(post("/api/v1/reservations/" + pending + "/review"), driver, "{\"rating\":6}").andExpect(status().isBadRequest());
    }

    @Test
    void delete_ByAuthorOrAdmin_RecomputesAverage() throws Exception {
        String first = completedReservation(driver, owner, garage.id());
        String second = completedReservation(otherDriver, owner, garage.id());
        String reviewA = read(performJson(post("/api/v1/reservations/" + first + "/review"), driver, "{\"rating\":2}"), "$.id");
        String reviewB = read(performJson(post("/api/v1/reservations/" + second + "/review"), otherDriver, "{\"rating\":4}"), "$.id");

        perform(delete("/api/v1/reviews/" + reviewA), otherDriver).andExpect(status().isForbidden());
        perform(delete("/api/v1/reviews/" + reviewA), driver).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/garages/" + garage.id())).andExpect(jsonPath("$.averageRating").value(4.0));
        perform(delete("/api/v1/reviews/" + reviewB), admin).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/garages/" + garage.id()))
                .andExpect(jsonPath("$.averageRating").value(0.0))
                .andExpect(jsonPath("$.ratingCount").value(0));
        perform(delete("/api/v1/reviews/" + reviewB), admin).andExpect(status().isNotFound());
    }
}
