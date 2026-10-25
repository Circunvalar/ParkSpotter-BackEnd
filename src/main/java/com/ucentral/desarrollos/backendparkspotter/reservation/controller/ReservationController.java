package com.ucentral.desarrollos.backendparkspotter.reservation.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VehicleType;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.CancelReservationRequest;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.ReservationCodeRequest;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.ReservationQuoteResponse;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.ReservationRequest;
import com.ucentral.desarrollos.backendparkspotter.reservation.dto.ReservationResponse;
import com.ucentral.desarrollos.backendparkspotter.reservation.entity.ReservationStatus;
import com.ucentral.desarrollos.backendparkspotter.reservation.service.ReservationService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Reservas: crear, consultar, cancelar, historial y entrada/salida con el código. */
@RestController
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** Cotización pública de una franja (precio y plazas libres) antes de reservar. */
    @GetMapping("/api/v1/garages/{garageId}/reservation-quote")
    public ResponseEntity<ReservationQuoteResponse> quote(@PathVariable UUID garageId,
                                                          @RequestParam VehicleType vehicleType,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
                                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
        return ResponseEntity.ok(reservationService.quote(garageId, vehicleType, startTime, endTime));
    }

    @PreAuthorize("hasAnyRole('DRIVER', 'ADMIN')")
    @PostMapping("/api/v1/reservations")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request,
                                                      @AuthenticationPrincipal UserAccount driver) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reservationService.create(request, driver));
    }

    @GetMapping("/api/v1/reservations/mine")
    public ResponseEntity<PageResponse<ReservationResponse>> mine(@AuthenticationPrincipal UserAccount driver,
                                                                  @RequestParam(required = false) ReservationStatus status,
                                                                  @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(reservationService.listMine(driver, status, page, size));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @GetMapping("/api/v1/reservations/owned")
    public ResponseEntity<PageResponse<ReservationResponse>> owned(@AuthenticationPrincipal UserAccount owner,
                                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                                   @RequestParam(required = false) UUID garageId,
                                                                   @RequestParam(required = false) ReservationStatus status,
                                                                   @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                                   @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(reservationService.listOwned(owner, date, garageId, status, page, size));
    }

    @GetMapping("/api/v1/reservations/{id}")
    public ResponseEntity<ReservationResponse> get(@PathVariable UUID id, @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(reservationService.get(id, user));
    }

    @PostMapping("/api/v1/reservations/{id}/cancel")
    public ResponseEntity<ReservationResponse> cancel(@PathVariable UUID id,
                                                      @Valid @RequestBody(required = false) CancelReservationRequest request,
                                                      @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(reservationService.cancel(id, user, request == null ? null : request.reason()));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/api/v1/reservations/check-in")
    public ResponseEntity<ReservationResponse> checkIn(@Valid @RequestBody ReservationCodeRequest request,
                                                       @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(reservationService.checkIn(request.code(), user));
    }

    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @PostMapping("/api/v1/reservations/check-out")
    public ResponseEntity<ReservationResponse> checkOut(@Valid @RequestBody ReservationCodeRequest request,
                                                        @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(reservationService.checkOut(request.code(), user));
    }
}
