package com.ucentral.desarrollos.backendparkspotter.reservation;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.SpotStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.ParkingSpotRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import com.ucentral.desarrollos.backendparkspotter.notification.repository.NotificationRepository;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.repository.ReservationRepository;
import com.ucentral.desarrollos.backendparkspotter.reservation.service.ReservationJobs;
import com.ucentral.desarrollos.backendparkspotter.reservation.service.ReservationService;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reservas por HTTP (Sprint 04-05): creación con asignación de plaza, reglas, cancelación, pago simulado,
 * entrada y salida, historial, panel del propietario y tareas automáticas.
 */
class ReservationApiTest extends ApiTestSupport {

    @Autowired ReservationRepository reservationRepository;
    @Autowired ReservationService reservationService;
    @Autowired ParkingSpotRepository spotRepository;
    @Autowired ParkingSpotService parkingSpotService;
    @Autowired NotificationRepository notificationRepository;

    UserAccount owner;
    UserAccount driver;
    UserAccount otherDriver;
    GarageResponse garage;

    @BeforeEach
    void setUp() {
        owner = owner("dueno@parkspotter.co");
        driver = driver("conductor@parkspotter.co");
        otherDriver = driver("otro@parkspotter.co");
        garage = garage24h(owner, "Garaje Reservas", 2);
    }

    // ---------- cotización ----------

    @Test
    void quote_IsPublic_AndReturnsPriceAndAvailableSpots() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/reservation-quote")
                        .param("vehicleType", "CAR")
                        .param("startTime", inMinutes(60).toString())
                        .param("endTime", inMinutes(130).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.availableSpots").value(2))
                .andExpect(jsonPath("$.durationMinutes").value(70))
                // 70 minutos = 3 bloques de 30 min = 1.5 h × 4000
                .andExpect(jsonPath("$.totalAmount").value(6000.0))
                .andExpect(jsonPath("$.currency").value("COP"));
    }

    @Test
    void quote_ForUnavailableTypeOrInvalidSlot_ExplainsWhy() throws Exception {
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/reservation-quote")
                        .param("vehicleType", "MOTORCYCLE")
                        .param("startTime", inMinutes(60).toString())
                        .param("endTime", inMinutes(120).toString()))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.unavailableReason").value(containsString("No hay plazas")));

        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/reservation-quote")
                        .param("vehicleType", "CAR")
                        .param("startTime", inMinutes(120).toString())
                        .param("endTime", inMinutes(60).toString()))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.durationMinutes").value(0))
                .andExpect(jsonPath("$.unavailableReason").value(containsString("posterior")));
    }

    // ---------- crear ----------

    @Test
    void create_FarInAdvance_AssignsSpotWithoutHoldingIt() throws Exception {
        reserve(driver, 180, 240)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.code").value(containsString("PS-")))
                .andExpect(jsonPath("$.spotCode").value("P-001"))
                .andExpect(jsonPath("$.vehiclePlate").value("ABC123"))
                .andExpect(jsonPath("$.totalAmount").value(4000.0))
                .andExpect(jsonPath("$.cancellable").value(true))
                .andExpect(jsonPath("$.paid").value(false));

        // La plaza sigue libre en tiempo real: se aparta solo cuando se acerca la hora.
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.reservedSpots").value(0));
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(driver.getId())).isEqualTo(1);
    }

    @Test
    void create_StartingNow_HoldsSpotAsReserved() throws Exception {
        reserve(driver, 1, 90).andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.reservedSpots").value(1))
                .andExpect(jsonPath("$.availableSpots").value(1));
    }

    @Test
    void create_OverlappingReservations_UseDifferentSpots_AndThirdGets409() throws Exception {
        reserve(driver, 120, 180).andExpect(jsonPath("$.spotCode").value("P-001"));
        reserve(otherDriver, 150, 210).andExpect(jsonPath("$.spotCode").value("P-002"));

        UserAccount third = driver("tercero@parkspotter.co");
        reserve(third, 160, 200)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("No hay plazas libres")));

        // Una franja que no se cruza sí encuentra plaza.
        reserve(third, 180, 240).andExpect(status().isCreated());
    }

    @Test
    void create_ConcurrentRequests_NeverAssignTheSameSpot() throws Exception {
        GarageResponse single = garage24h(owner, "Una plaza", 1);
        List<UserAccount> drivers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            drivers.add(driver("concurrente" + i + "@parkspotter.co"));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Integer>> results = new ArrayList<>();
        for (UserAccount d : drivers) {
            Callable<Integer> call = () -> reserveIn(single.id(), d, 120, 180).andReturn().getResponse().getStatus();
            results.add(pool.submit(call));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get());
        }
        pool.shutdown();

        assertThat(statuses).containsOnlyOnce(201);
        assertThat(statuses).filteredOn(code -> code != 201).allMatch(code -> code == 409);
    }

    @Test
    void create_ChoosingSpecificSpot_UsesItOrRejectsIt() throws Exception {
        UUID secondSpot = spotRepository.findByGarageFiltered(garage.id(), null, null).get(1).getId();
        String body = reservationBody(garage.id(), 120, 180, "\"spotId\":\"" + secondSpot + "\"");
        performJson(post("/api/v1/reservations"), driver, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spotId").value(secondSpot.toString()));

        performJson(post("/api/v1/reservations"), otherDriver, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("plaza elegida")));
    }

    @Test
    void create_RejectsInvalidSlotsAndRules() throws Exception {
        reserve(driver, 60, 70).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("al menos 30 minutos")));
        reserve(driver, 60, 60 + 25 * 60).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("más de 24 horas")));
        reserve(driver, -60, 30).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("pasado")));
        reserve(driver, 31L * 24 * 60, 31L * 24 * 60 + 60).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("anticipación")));
        reserve(driver, 120, 60).andExpect(status().isBadRequest());

        // Validación del cuerpo: placa inválida y campos obligatorios.
        performJson(post("/api/v1/reservations"), driver,
                "{\"garageId\":\"" + garage.id() + "\",\"vehicleType\":\"CAR\",\"vehiclePlate\":\"!!\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.vehiclePlate").exists())
                .andExpect(jsonPath("$.details.startTime").exists());
    }

    @Test
    void create_OverlappingWithOwnReservation_Returns409() throws Exception {
        reserve(driver, 120, 180).andExpect(status().isCreated());
        reserve(driver, 150, 200)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Ya tienes una reserva")));
    }

    @Test
    void create_InClosedOrInactiveGarage_IsRejected() throws Exception {
        ZoneId zone = ZoneId.of("America/Bogota");
        LocalTime now = LocalTime.now(zone);
        // Garaje abierto solo una hora que ya pasó o no incluye la franja.
        GarageResponse dayOnly = garageWithSchedule(owner, "Horario corto", now.plusHours(3), now.plusHours(4));
        reserveIn(dayOnly.id(), driver, 60, 120)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no está abierto")));

        garageService.changeStatus(garage.id(), GarageStatus.INACTIVE, owner);
        reserve(driver, 60, 120).andExpect(status().isConflict());
    }

    @Test
    void create_WithoutDriverRole_Returns403_AndUnknownGarage404() throws Exception {
        reserve(owner, 60, 120).andExpect(status().isForbidden());

        reserveIn(UUID.randomUUID(), driver, 60, 120).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/garages/" + UUID.randomUUID() + "/reservation-quote")
                        .param("vehicleType", "CAR")
                        .param("startTime", inMinutes(60).toString())
                        .param("endTime", inMinutes(120).toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_RecordsSearchToReservationTime() throws Exception {
        String body = reservationBody(garage.id(), 120, 180, "\"searchStartedAt\":\"" + Instant.now().minusSeconds(95) + "\"");
        String id = read(performJson(post("/api/v1/reservations"), driver, body), "$.id");

        Integer seconds = reservationRepository.findById(UUID.fromString(id)).orElseThrow().getSearchToReservationSeconds();
        assertThat(seconds).isBetween(94, 120);

        String future = reservationBody(garage.id(), 200, 260, "\"searchStartedAt\":\"" + Instant.now().plusSeconds(600) + "\"");
        String id2 = read(performJson(post("/api/v1/reservations"), driver, future), "$.id");
        assertThat(reservationRepository.findById(UUID.fromString(id2)).orElseThrow().getSearchToReservationSeconds()).isNull();
    }

    // ---------- consultar ----------

    @Test
    void get_VisibleToDriverOwnerAndAdmin_ButNotToStrangers() throws Exception {
        String id = read(reserve(driver, 120, 180), "$.id");

        perform(get("/api/v1/reservations/" + id), driver).andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName").value("Usuario conductor"));
        perform(get("/api/v1/reservations/" + id), owner).andExpect(status().isOk());
        perform(get("/api/v1/reservations/" + id), admin("admin@parkspotter.co")).andExpect(status().isOk());
        perform(get("/api/v1/reservations/" + id), otherDriver).andExpect(status().isForbidden());
        perform(get("/api/v1/reservations/" + UUID.randomUUID()), driver).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/reservations/" + id)).andExpect(status().isUnauthorized());
    }

    @Test
    void history_ListsOwnReservationsNewestFirst_AndFiltersByStatus() throws Exception {
        String first = read(reserve(driver, 120, 180), "$.id");
        String second = read(reserve(driver, 300, 360), "$.id");
        perform(post("/api/v1/reservations/" + first + "/cancel"), driver).andExpect(status().isOk());
        reserve(otherDriver, 120, 180);

        perform(get("/api/v1/reservations/mine"), driver)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second));

        perform(get("/api/v1/reservations/mine").param("status", "CANCELLED"), driver)
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(first));
    }

    @Test
    void owned_ListsReservationsOfOwnGaragesForTheDay() throws Exception {
        reserve(driver, 60, 120);
        UserAccount otherOwner = owner("otro-dueno@parkspotter.co");
        GarageResponse foreign = garage24h(otherOwner, "Ajeno", 1);
        reserveIn(foreign.id(), otherDriver, 60, 120);
        LocalDate today = LocalDate.ofInstant(inMinutes(60), ZoneId.of("America/Bogota"));

        perform(get("/api/v1/reservations/owned").param("date", today.toString()), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].garageId").value(garage.id().toString()));

        perform(get("/api/v1/reservations/owned").param("garageId", foreign.id().toString())
                .param("status", "PENDING_PAYMENT"), admin("admin@parkspotter.co"))
                .andExpect(jsonPath("$.totalElements").value(1));

        perform(get("/api/v1/reservations/owned").param("date", today.plusDays(3).toString()), owner)
                .andExpect(jsonPath("$.totalElements").value(0));
        perform(get("/api/v1/reservations/owned"), driver).andExpect(status().isForbidden());
    }

    // ---------- cancelar ----------

    @Test
    void cancel_ByDriver_ReleasesHeldSpotAndNotifiesOwner() throws Exception {
        String id = read(reserve(driver, 1, 90), "$.id");

        performJson(post("/api/v1/reservations/" + id + "/cancel"), driver, "{\"reason\":\"  Cambié de planes \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Cambié de planes"))
                .andExpect(jsonPath("$.cancellable").value(false));

        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.reservedSpots").value(0))
                .andExpect(jsonPath("$.availableSpots").value(2));
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(owner.getId())).isEqualTo(1);

        perform(post("/api/v1/reservations/" + id + "/cancel"), driver)
                .andExpect(status().isConflict());
    }

    @Test
    void cancel_ByGarageOwnerAllowed_ByStrangerForbidden() throws Exception {
        String id = read(reserve(driver, 120, 180), "$.id");

        perform(post("/api/v1/reservations/" + id + "/cancel"), otherDriver).andExpect(status().isForbidden());
        performJson(post("/api/v1/reservations/" + id + "/cancel"), owner, "{\"reason\":\" \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellationReason").doesNotExist());
    }

    // ---------- pago, entrada y salida ----------

    @Test
    void fullFlow_PayCheckInCheckOut() throws Exception {
        ResultActions created = reserve(driver, 1, 61);
        String id = read(created, "$.id");
        String code = read(created, "$.code");

        payAndApprove(id);
        perform(get("/api/v1/reservations/" + id), driver)
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paid").value(true));

        // El código se acepta sin el prefijo y en minúsculas.
        performJson(post("/api/v1/reservations/check-in"), owner, "{\"code\":\"" + code.substring(3).toLowerCase() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN"))
                .andExpect(jsonPath("$.checkInAt").exists());
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.occupiedSpots").value(1))
                .andExpect(jsonPath("$.reservedSpots").value(0));

        performJson(post("/api/v1/reservations/check-out"), owner, "{\"code\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.overtimeMinutes").value(0));
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.availableSpots").value(2));

        performJson(post("/api/v1/reservations/check-out"), owner, "{\"code\":\"" + code + "\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void checkOut_AfterEndTime_ComputesOvertime() throws Exception {
        ResultActions created = reserve(driver, 1, 61);
        String id = read(created, "$.id");
        String code = read(created, "$.code");
        payAndApprove(id);
        performJson(post("/api/v1/reservations/check-in"), owner, "{\"code\":\"" + code + "\"}").andExpect(status().isOk());

        // Se simula que la franja terminó hace 40 minutos.
        Reservation reservation = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        reservation.setEndTime(Instant.now().minus(40, ChronoUnit.MINUTES));
        reservationRepository.save(reservation);

        performJson(post("/api/v1/reservations/check-out"), owner, "{\"code\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overtimeMinutes").value(40))
                // 40 min = 2 bloques de 30 = 4000
                .andExpect(jsonPath("$.overtimeAmount").value(4000.0));
    }

    @Test
    void checkIn_Rules() throws Exception {
        ResultActions created = reserve(driver, 180, 240);
        String id = read(created, "$.id");
        String code = read(created, "$.code");
        String body = "{\"code\":\"" + code + "\"}";

        performJson(post("/api/v1/reservations/check-in"), owner, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("no está pagada")));

        payAndApprove(id);
        performJson(post("/api/v1/reservations/check-in"), owner, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("temprano")));

        UserAccount otherOwner = owner("otro-dueno@parkspotter.co");
        performJson(post("/api/v1/reservations/check-in"), otherOwner, body).andExpect(status().isForbidden());
        performJson(post("/api/v1/reservations/check-in"), driver, body).andExpect(status().isForbidden());
        performJson(post("/api/v1/reservations/check-in"), owner, "{\"code\":\"PS-NOEXIS\"}").andExpect(status().isNotFound());
        performJson(post("/api/v1/reservations/check-in"), owner, "{\"code\":\"!\"}").andExpect(status().isBadRequest());

        Reservation reservation = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        reservation.setStartTime(Instant.now().minus(3, ChronoUnit.HOURS));
        reservation.setEndTime(Instant.now().minus(1, ChronoUnit.HOURS));
        reservationRepository.save(reservation);
        performJson(post("/api/v1/reservations/check-in"), owner, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("terminó")));

        reservationService.cancel(UUID.fromString(id), driver, null);
        performJson(post("/api/v1/reservations/check-in"), owner, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("CANCELLED")));
    }

    @Test
    void garageWithActiveReservations_CannotBeDeleted_NorItsReservedSpot() throws Exception {
        String id = read(reserve(driver, 180, 240), "$.id");
        UUID spotId = reservationRepository.findById(UUID.fromString(id)).orElseThrow().getSpot().getId();

        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/garages/" + garage.id()), owner)
                .andExpect(status().isConflict());
        perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(
                "/api/v1/garages/" + garage.id() + "/spots/" + spotId), owner)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("reservas activas")));
        performJson(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/garages/" + garage.id()), owner,
                "{\"totalSpots\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpots").value(1));
        // Se eliminó la plaza libre, nunca la que tiene la reserva.
        assertThat(spotRepository.findById(spotId)).isPresent();
    }

    // ---------- tareas automáticas ----------

    @Test
    void jobs_ExpireUnpaid_ReleaseSpot() throws Exception {
        String id = read(reserve(driver, 1, 60), "$.id");
        Reservation reservation = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        reservation.setPaymentDeadline(Instant.now().minusSeconds(5));
        reservationRepository.save(reservation);

        new ReservationJobs(reservationService).run();

        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(ReservationStatus.EXPIRED);
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.reservedSpots").value(0));
        // Idempotente: una segunda pasada no hace nada.
        assertThat(reservationService.expire(UUID.fromString(id))).isFalse();
    }

    @Test
    void jobs_HoldSpotWhenReservationIsAboutToStart_ReassigningIfOccupied() throws Exception {
        String id = read(reserve(driver, 180, 240), "$.id");
        payAndApprove(id);
        Reservation reservation = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        UUID assigned = reservation.getSpot().getId();
        reservation.setStartTime(Instant.now().plus(10, ChronoUnit.MINUTES));
        reservationRepository.save(reservation);
        // Un vehículo sin reserva ocupa la plaza asignada.
        parkingSpotService.changeStatus(garage.id(), assigned, SpotStatus.OCCUPIED, owner);

        new ReservationJobs(reservationService).run();

        Reservation held = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(held.isSpotHeld()).isTrue();
        assertThat(held.getSpot().getId()).isNotEqualTo(assigned);
        assertThat(spotRepository.findById(held.getSpot().getId()).orElseThrow().getStatus()).isEqualTo(SpotStatus.RESERVED);
        assertThat(reservationService.hold(UUID.fromString(id))).isFalse();
        perform(get("/api/v1/notifications"), driver)
                .andExpect(jsonPath("$.content[0].type").value("RESERVATION_REMINDER"));
    }

    @Test
    void jobs_HoldFails_WhenNoSpotIsFree() throws Exception {
        GarageResponse single = garage24h(owner, "Una plaza", 1);
        String id = read(reserveIn(single.id(), driver, 180, 240), "$.id");
        Reservation reservation = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        reservation.setStartTime(Instant.now().plus(10, ChronoUnit.MINUTES));
        reservationRepository.save(reservation);
        parkingSpotService.changeStatus(single.id(), reservation.getSpot().getId(), SpotStatus.OCCUPIED, owner);

        assertThat(reservationService.hold(UUID.fromString(id))).isFalse();
    }

    @Test
    void jobs_MarkNoShow_WhenConfirmedSlotEndsWithoutCheckIn() throws Exception {
        String id = read(reserve(driver, 1, 60), "$.id");
        payAndApprove(id);
        Reservation reservation = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        reservation.setStartTime(Instant.now().minus(2, ChronoUnit.HOURS));
        reservation.setEndTime(Instant.now().minus(1, ChronoUnit.HOURS));
        reservationRepository.save(reservation);

        new ReservationJobs(reservationService).run();

        assertThat(reservationRepository.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(ReservationStatus.NO_SHOW);
        mockMvc.perform(get("/api/v1/garages/" + garage.id() + "/availability"))
                .andExpect(jsonPath("$.availableSpots").value(2));
        assertThat(reservationService.markNoShow(UUID.fromString(id))).isFalse();
    }

    @Test
    void confirmPayment_ForExpiredReservation_ReturnsFalse() throws Exception {
        String id = read(reserve(driver, 120, 180), "$.id");
        Reservation reservation = reservationRepository.findById(UUID.fromString(id)).orElseThrow();
        reservation.setStatus(ReservationStatus.EXPIRED);
        reservationRepository.save(reservation);

        assertThat(reservationService.confirmPayment(UUID.fromString(id))).isFalse();
    }

    // ---------- helpers ----------

    private ResultActions reserve(UserAccount user, long startInMinutes, long endInMinutes) throws Exception {
        return reserveIn(garage.id(), user, startInMinutes, endInMinutes);
    }

    private ResultActions reserveIn(UUID garageId, UserAccount user, long startInMinutes, long endInMinutes) throws Exception {
        return performJson(post("/api/v1/reservations"), user, reservationBody(garageId, startInMinutes, endInMinutes, null));
    }

    private static String reservationBody(UUID garageId, long startInMinutes, long endInMinutes, String extra) {
        return "{\"garageId\":\"" + garageId + "\",\"vehicleType\":\"CAR\",\"vehiclePlate\":\"abc 123\","
                + "\"startTime\":\"" + inMinutes(startInMinutes) + "\",\"endTime\":\"" + inMinutes(endInMinutes) + "\""
                + (extra == null ? "" : "," + extra) + "}";
    }

    private void payAndApprove(String reservationId) throws Exception {
        String paymentId = read(perform(post("/api/v1/reservations/" + reservationId + "/payments"), driver)
                .andExpect(status().isCreated()), "$.id");
        performJson(post("/api/v1/payments/" + paymentId + "/sandbox-result"), driver, "{\"status\":\"APPROVED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }
}
