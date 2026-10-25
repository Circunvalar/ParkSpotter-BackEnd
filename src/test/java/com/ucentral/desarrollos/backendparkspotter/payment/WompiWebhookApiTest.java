package com.ucentral.desarrollos.backendparkspotter.payment;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.security.SecureTokens;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pagos con Wompi (Sprint 05, T5-03/T5-04): checkout firmado y webhook con firma e idempotencia. */
@TestPropertySource(properties = {
        "app.payments.provider=wompi",
        "app.payments.wompi.public-key=pub_test_abc",
        "app.payments.wompi.integrity-secret=test_integrity_secret",
        "app.payments.wompi.events-secret=test_events_secret"
})
class WompiWebhookApiTest extends ApiTestSupport {

    static final String EVENTS_SECRET = "test_events_secret";

    @Autowired ReservationRepository reservationRepository;

    UserAccount driver;
    GarageResponse garage;

    @BeforeEach
    void setUp() {
        UserAccount owner = owner("dueno-wompi@parkspotter.co");
        driver = driver("conductor-wompi@parkspotter.co");
        garage = garage24h(owner, "Garaje Wompi", 2);
    }

    @Test
    void checkout_IsSignedForWompi() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("WOMPI"))
                .andExpect(jsonPath("$.publicKey").value("pub_test_abc"))
                .andExpect(jsonPath("$.integritySignature").isNotEmpty())
                .andExpect(jsonPath("$.checkoutUrl").value(containsString("checkout.wompi.co")));
    }

    @Test
    void approvedWebhook_ConfirmsReservation_AndRepeatedEventIsDuplicate() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");
        String reference = read(perform(get("/api/v1/payments/" + paymentId), driver), "$.reference");

        String body = event("tx-1", "APPROVED", reference, 400000);
        webhook(body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(false));
        webhook(body).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));

        perform(get("/api/v1/payments/" + paymentId), driver)
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.paymentMethod").value("CARD"));
        perform(get("/api/v1/reservations/" + reservationId), driver)
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        perform(post("/api/v1/payments/" + paymentId + "/sandbox-result")
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"), driver)
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvedWebhook_WithWrongAmount_MarksError() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");
        String reference = read(perform(get("/api/v1/payments/" + paymentId), driver), "$.reference");

        webhook(event("tx-2", "APPROVED", reference, 100)).andExpect(status().isOk());
        perform(get("/api/v1/payments/" + paymentId), driver).andExpect(jsonPath("$.status").value("ERROR"));
        perform(get("/api/v1/reservations/" + reservationId), driver).andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void declinedWebhook_AndApprovalAfterExpiry() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");
        String reference = read(perform(get("/api/v1/payments/" + paymentId), driver), "$.reference");

        webhook(event("tx-3", "DECLINED", reference, 400000)).andExpect(status().isOk());
        perform(get("/api/v1/payments/" + paymentId), driver).andExpect(jsonPath("$.status").value("DECLINED"));

        Reservation reservation = reservationRepository.findById(UUID.fromString(reservationId)).orElseThrow();
        reservation.setStatus(ReservationStatus.EXPIRED);
        reservationRepository.save(reservation);
        webhook(event("tx-4", "APPROVED", reference, 400000)).andExpect(status().isOk());
        perform(get("/api/v1/payments/" + paymentId), driver).andExpect(jsonPath("$.status").value("REFUND_PENDING"));
    }

    @Test
    void webhook_UnknownReference_IsAcknowledged() throws Exception {
        webhook(event("tx-5", "APPROVED", "PS-NOEXISTE-1", 1000)).andExpect(status().isOk());
        webhook(event("tx-6", "APPROVED", null, 1000)).andExpect(status().isOk());
    }

    @Test
    void webhook_WithBadSignature_IsUnauthorized() throws Exception {
        String body = event("tx-7", "APPROVED", "PS-X-1", 1000).replace("\"checksum\":\"", "\"checksum\":\"00");
        webhook(body).andExpect(status().isUnauthorized());
        webhook("no es json").andExpect(status().isUnauthorized());
    }

    private ResultActions webhook(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/payments/webhooks/wompi")
                .header("X-Event-Checksum", "ignorado")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    static String event(String transactionId, String status, String reference, long amountInCents) {
        long timestamp = 1_700_000_000L;
        String checksum = SecureTokens.sha256Hex(transactionId + status + amountInCents + timestamp + EVENTS_SECRET);
        String referenceJson = reference == null ? "null" : "\"" + reference + "\"";
        return "{\"event\":\"transaction.updated\",\"data\":{\"transaction\":{\"id\":\"" + transactionId + "\","
                + "\"status\":\"" + status + "\",\"amount_in_cents\":" + amountInCents + ",\"reference\":" + referenceJson + ","
                + "\"payment_method_type\":\"CARD\"}},"
                + "\"signature\":{\"properties\":[\"transaction.id\",\"transaction.status\",\"transaction.amount_in_cents\"],"
                + "\"checksum\":\"" + checksum + "\"},\"timestamp\":" + timestamp + "}";
    }
}
