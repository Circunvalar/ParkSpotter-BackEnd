package com.ucentral.desarrollos.backendparkspotter.report;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reportes del propietario y del administrador (Sprint 06, T6-04). */
class ReportApiTest extends ApiTestSupport {

    @Autowired ReservationRepository reservationRepository;

    UserAccount owner;
    UserAccount driver;
    UserAccount admin;
    GarageResponse garage;
    GarageResponse emptyGarage;

    @BeforeEach
    void setUp() {
        owner = owner("dueno-reportes@parkspotter.co");
        driver = driver("conductor-reportes@parkspotter.co");
        admin = admin("admin-reportes@parkspotter.co");
        garage = garage24h(owner, "Garaje Reportes", 2);
        emptyGarage = garage24h(owner, "Garaje Vacío", 1);
    }

    @Test
    void ownerReport_SummarizesPaidCompletedAndCancelled() throws Exception {
        String completed = completedReservation(driver, owner, garage.id());
        performJson(post("/api/v1/reservations/" + completed + "/review"), driver, "{\"rating\":4}");
        String cancelled = reserveId(driver, garage.id(), 120, 180);
        performJson(post("/api/v1/reservations/" + cancelled + "/cancel"), driver, "{}");
        Reservation withSearch = reservationRepository.findById(UUID.fromString(completed)).orElseThrow();
        withSearch.setSearchToReservationSeconds(90);
        reservationRepository.save(withSearch);

        // La reserva cancelada empieza en 2 h: después de las 22:00 de Bogotá cae en el día siguiente,
        // así que el rango termina mañana (con el rango por defecto, que termina hoy, quedaría fuera).
        LocalDate to = LocalDate.now(ZoneId.of("America/Bogota")).plusDays(1);
        String from = to.minusDays(29).toString();
        perform(get("/api/v1/reports/owner").param("from", from).param("to", to.toString()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalReservations").value(2))
                .andExpect(jsonPath("$.summary.paidReservations").value(1))
                .andExpect(jsonPath("$.summary.completed").value(1))
                .andExpect(jsonPath("$.summary.cancelled").value(1))
                .andExpect(jsonPath("$.summary.revenue").value(4000.0))
                .andExpect(jsonPath("$.summary.averageRating").value(4.0))
                .andExpect(jsonPath("$.summary.occupancyRate").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.garages", hasSize(2)))
                .andExpect(jsonPath("$.garages[0].name").value("Garaje Reportes"))
                .andExpect(jsonPath("$.daily", hasSize(30)));

        perform(get("/api/v1/reports/owner").param("garageId", emptyGarage.id().toString()), owner)
                .andExpect(jsonPath("$.summary.totalReservations").value(0))
                .andExpect(jsonPath("$.garages", hasSize(1)));

        perform(get("/api/v1/reports/admin").param("from", from).param("to", to.toString()), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(3))
                .andExpect(jsonPath("$.users.drivers").value(1))
                .andExpect(jsonPath("$.garages.total").value(2))
                .andExpect(jsonPath("$.averageSearchToReservationSeconds").value(90.0))
                .andExpect(jsonPath("$.topGarages", hasSize(1)));
    }

    @Test
    void reports_EmptyOwner_InvalidRanges_AndPermissions() throws Exception {
        UserAccount newOwner = owner("nuevo-reportes@parkspotter.co");
        LocalDate today = LocalDate.now();
        perform(get("/api/v1/reports/owner").param("from", today.toString()).param("to", today.toString()), newOwner)
                .andExpect(jsonPath("$.garages", hasSize(0)))
                .andExpect(jsonPath("$.summary.occupancyRate").value(0.0))
                .andExpect(jsonPath("$.daily", hasSize(1)));
        perform(get("/api/v1/reports/admin").param("from", today.toString()), admin)
                .andExpect(jsonPath("$.averageSearchToReservationSeconds").doesNotExist());

        perform(get("/api/v1/reports/owner").param("from", today.toString()).param("to", today.minusDays(1).toString()), owner)
                .andExpect(status().isBadRequest());
        perform(get("/api/v1/reports/owner").param("from", today.minusDays(400).toString()), owner)
                .andExpect(status().isBadRequest());
        perform(get("/api/v1/reports/owner").param("garageId", garage.id().toString()), newOwner)
                .andExpect(status().isForbidden());
        perform(get("/api/v1/reports/owner"), driver).andExpect(status().isForbidden());
        perform(get("/api/v1/reports/admin"), owner).andExpect(status().isForbidden());
    }
}
