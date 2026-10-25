package com.ucentral.desarrollos.backendparkspotter.payment;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus;
import com.ucentral.desarrollos.backendparkspotter.payment.repository.PaymentRepository;
import com.ucentral.desarrollos.backendparkspotter.payment.service.PaymentService;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Pagos con la pasarela simulada (Sprint 05): creación, reglas, resultados, consulta y comprobante. */
class PaymentApiTest extends ApiTestSupport {

    @Autowired ReservationRepository reservationRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PaymentService paymentService;

    UserAccount owner;
    UserAccount driver;
    UserAccount stranger;
    GarageResponse garage;

    @BeforeEach
    void setUp() {
        owner = owner("dueno-pagos@parkspotter.co");
        driver = driver("conductor-pagos@parkspotter.co");
        stranger = driver("ajeno-pagos@parkspotter.co");
        garage = garage24h(owner, "Garaje Pagos", 2);
    }

    @Test
    void create_ReusesPendingPayment_WhenRedirectIsTheSame() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String body = "{\"redirectUrl\":\"http://localhost:3000/pagos/ok\"}";

        String first = read(performJson(post("/api/v1/reservations/" + reservationId + "/payments"), driver, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.provider").value("SANDBOX"))
                .andExpect(jsonPath("$.amountInCents").value(400000))
                .andExpect(jsonPath("$.checkoutUrl").value(containsString("sandbox=true"))), "$.id");
        String second = read(performJson(post("/api/v1/reservations/" + reservationId + "/payments"), driver, body)
                .andExpect(status().isCreated()), "$.id");
        assertThat(second).isEqualTo(first);

        // Otra URL de retorno crea un pago nuevo con referencia consecutiva.
        performJson(post("/api/v1/reservations/" + reservationId + "/payments"), driver, "{\"redirectUrl\":\"parkspotter://pago\"}")
                .andExpect(jsonPath("$.reference").value(containsString("-2")));

        perform(get("/api/v1/reservations/" + reservationId + "/payments"), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        perform(get("/api/v1/payments/" + first), driver)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(reservationId));
    }

    @Test
    void create_Rejects_ForeignDriver_BadRedirect_AndUnknownReservation() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);

        perform(post("/api/v1/reservations/" + reservationId + "/payments"), stranger).andExpect(status().isForbidden());
        performJson(post("/api/v1/reservations/" + reservationId + "/payments"), driver, "{\"redirectUrl\":\"https://malo.com/\"}")
                .andExpect(status().isBadRequest());
        perform(post("/api/v1/reservations/" + UUID.randomUUID() + "/payments"), driver).andExpect(status().isNotFound());
        perform(get("/api/v1/reservations/" + UUID.randomUUID() + "/payments"), driver).andExpect(status().isNotFound());
        perform(get("/api/v1/reservations/" + reservationId + "/payments"), stranger).andExpect(status().isForbidden());
        perform(get("/api/v1/payments/" + UUID.randomUUID()), driver).andExpect(status().isNotFound());
    }

    @Test
    void create_Rejects_WhenNotPending_OrDeadlinePassed() throws Exception {
        String paid = reserveId(driver, garage.id(), 60, 120);
        payApproved(driver, paid);
        perform(post("/api/v1/reservations/" + paid + "/payments"), driver).andExpect(status().isConflict());

        String late = reserveId(driver, garage.id(), 200, 260);
        Reservation reservation = reservationRepository.findById(UUID.fromString(late)).orElseThrow();
        reservation.setPaymentDeadline(Instant.now().minusSeconds(60));
        reservationRepository.save(reservation);
        perform(post("/api/v1/reservations/" + late + "/payments"), driver).andExpect(status().isConflict());
    }

    @Test
    void sandboxResult_DeclinedThenApproved_AndDuplicateIsIgnored() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");

        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"DECLINED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"DECLINED\"}")
                .andExpect(jsonPath("$.status").value("DECLINED"));
        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"APPROVED\"}")
                .andExpect(jsonPath("$.status").value("APPROVED"));
        // Un pago aprobado no retrocede.
        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"ERROR\"}")
                .andExpect(jsonPath("$.status").value("APPROVED"));
        perform(get("/api/v1/notifications"), driver)
                .andExpect(jsonPath("$.content[?(@.type == 'PAYMENT_DECLINED')]").exists());
    }

    @Test
    void sandboxResult_Rejects_UnsupportedStatus_AndForeignDriver() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");

        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"VOIDED\"}")
                .andExpect(status().isBadRequest());
        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), stranger, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isForbidden());
    }

    @Test
    void sandboxResult_RejectsNonSandboxPayment() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");
        var payment = paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow();
        payment.setProvider("WOMPI");
        paymentRepository.save(payment);

        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void approvalAfterCancellation_MarksRefundPending() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver), "$.id");
        performJson(post("/api/v1/reservations/" + reservationId + "/cancel"), driver, "{\"reason\":\"Cambio de planes\"}")
                .andExpect(status().isOk());

        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"APPROVED\"}")
                .andExpect(jsonPath("$.status").value("REFUND_PENDING"));
    }

    @Test
    void cancellingPaidReservation_MarksRefundPending_AndReceiptStillWorks() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 120, 180);
        String paymentId = payApproved(driver, reservationId);

        perform(get("/api/v1/reservations/" + reservationId + "/receipt"), driver)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receiptNumber").value(containsString("RC-PS-")))
                .andExpect(jsonPath("$.garageName").value("Garaje Pagos"))
                .andExpect(jsonPath("$.paymentStatus").value("APPROVED"));

        performJson(post("/api/v1/reservations/" + reservationId + "/cancel"), driver, "{}").andExpect(status().isOk());
        assertThat(paymentRepository.findById(UUID.fromString(paymentId)).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.REFUND_PENDING);
        perform(get("/api/v1/reservations/" + reservationId + "/receipt"), owner)
                .andExpect(jsonPath("$.paymentStatus").value("REFUND_PENDING"));
    }

    @Test
    void receipt_Conflict_WithoutApprovedPayment_AndNotFound() throws Exception {
        String reservationId = reserveId(driver, garage.id(), 60, 120);
        perform(get("/api/v1/reservations/" + reservationId + "/receipt"), driver).andExpect(status().isConflict());
        perform(get("/api/v1/reservations/" + UUID.randomUUID() + "/receipt"), driver).andExpect(status().isNotFound());
    }

    @Test
    void webhook_WithSandboxProvider_IsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhooks/wompi")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
