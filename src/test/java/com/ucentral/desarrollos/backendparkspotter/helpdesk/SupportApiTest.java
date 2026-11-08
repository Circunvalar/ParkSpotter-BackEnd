package com.ucentral.desarrollos.backendparkspotter.helpdesk;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Módulo de soporte (RNF-10): preguntas frecuentes, contacto y casos. */
class SupportApiTest extends ApiTestSupport {

    UserAccount driver;
    UserAccount other;
    UserAccount admin;

    @BeforeEach
    void setUp() {
        driver = driver("conductor-soporte@parkspotter.co");
        other = driver("otro-soporte@parkspotter.co");
        admin = admin("admin-soporte@parkspotter.co");
    }

    private String ticket(UserAccount user, String subject) throws Exception {
        return read(performJson(post("/api/v1/support/tickets"), user,
                "{\"subject\":\"" + subject + "\",\"category\":\"PAYMENT\",\"message\":\"No me llegó el comprobante del pago\"}")
                .andExpect(status().isCreated()), "$.id");
    }

    @Test
    void faqsAndContact_ArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/support/faqs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(5)));
        mockMvc.perform(get("/api/v1/support/faqs").param("category", "PAYMENT"))
                .andExpect(jsonPath("$[*].category", everyItem(is("PAYMENT"))));
        mockMvc.perform(get("/api/v1/support/contact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("soporte@parkspotter.co"));
        mockMvc.perform(get("/api/v1/support/tickets/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    void ticketLifecycle_UserAndSupportConversation() throws Exception {
        String id = ticket(driver, "Problema con el pago");
        perform(get("/api/v1/support/tickets/" + id), driver)
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.messages", hasSize(1)))
                .andExpect(jsonPath("$.userEmail").doesNotExist());
        perform(get("/api/v1/support/tickets/" + id), other).andExpect(status().isForbidden());

        perform(get("/api/v1/admin/support/tickets").param("status", "OPEN"), admin)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].userEmail").value("conductor-soporte@parkspotter.co"));
        perform(get("/api/v1/admin/support/tickets"), driver).andExpect(status().isForbidden());

        performJson(post("/api/v1/support/tickets/" + id + "/messages"), admin, "{\"body\":\"Ya lo reenviamos a tu correo\"}")
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.messages[1].fromStaff").value(true))
                .andExpect(jsonPath("$.messages[1].authorName").value("Soporte ParkSpotter"));
        perform(get("/api/v1/notifications"), driver)
                .andExpect(jsonPath("$.content[0].type").value("SUPPORT_UPDATE"))
                .andExpect(jsonPath("$.content[0].referenceId").value(id));

        performJson(patch("/api/v1/admin/support/tickets/" + id + "/status"), admin, "{\"status\":\"RESOLVED\"}")
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists());
        // Si el usuario responde, el caso se reabre.
        performJson(post("/api/v1/support/tickets/" + id + "/messages"), driver, "{\"body\":\"Sigue sin llegar\"}")
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        performJson(patch("/api/v1/admin/support/tickets/" + id + "/status"), admin, "{\"status\":\"CLOSED\"}")
                .andExpect(jsonPath("$.status").value("CLOSED"));
        performJson(post("/api/v1/support/tickets/" + id + "/messages"), driver, "{\"body\":\"Hola\"}")
                .andExpect(status().isConflict());

        perform(get("/api/v1/support/tickets/mine"), driver)
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].messageCount").value(3));
        perform(get("/api/v1/support/tickets/mine").param("status", "OPEN"), driver)
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void create_ValidatesLimitsAndReservationOwnership() throws Exception {
        performJson(post("/api/v1/support/tickets"), driver, "{\"subject\":\"Hola\",\"category\":\"OTHER\",\"message\":\"corto\"}")
                .andExpect(status().isBadRequest());
        performJson(post("/api/v1/support/tickets"), driver,
                "{\"subject\":\"Reserva\",\"category\":\"RESERVATION\",\"message\":\"Mensaje suficientemente largo\",\"reservationId\":\""
                        + UUID.randomUUID() + "\"}")
                .andExpect(status().isNotFound());

        UserAccount owner = owner("dueno-soporte@parkspotter.co");
        GarageResponse garage = garage24h(owner, "Garaje Soporte", 2);
        String reservation = reserveId(driver, garage.id(), 60, 120);
        String body = "{\"subject\":\"Reserva\",\"category\":\"RESERVATION\",\"message\":\"Mensaje suficientemente largo\",\"reservationId\":\""
                + reservation + "\"}";
        performJson(post("/api/v1/support/tickets"), other, body).andExpect(status().isForbidden());
        performJson(post("/api/v1/support/tickets"), owner, body).andExpect(status().isCreated());
        performJson(post("/api/v1/support/tickets"), driver, body)
                .andExpect(jsonPath("$.reservationId").value(reservation));

        for (int i = 0; i < 4; i++) {
            ticket(other, "Caso número " + i);
        }
        ticket(other, "Caso número 5");
        performJson(post("/api/v1/support/tickets"), other,
                "{\"subject\":\"Uno más\",\"category\":\"OTHER\",\"message\":\"Mensaje suficientemente largo\"}")
                .andExpect(status().isConflict());
        perform(get("/api/v1/support/tickets/" + UUID.randomUUID()), driver).andExpect(status().isNotFound());
        performJson(patch("/api/v1/admin/support/tickets/" + UUID.randomUUID() + "/status"), admin, "{\"status\":\"CLOSED\"}")
                .andExpect(status().isNotFound());
    }
}
