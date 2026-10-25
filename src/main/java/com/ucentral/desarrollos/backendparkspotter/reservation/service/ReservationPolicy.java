package com.ucentral.desarrollos.backendparkspotter.reservation.service;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.OpeningHours;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Reglas de negocio de las reservas (Sprint 04, T4-03), configurables con app.reservations.*:
 * duración mínima y máxima, anticipación máxima, horario del garaje, tiempo para pagar y precio.
 */
@Getter
@Component
public class ReservationPolicy {

    /** Tolerancia para una franja que "empieza ahora" y llega unos segundos tarde por la red. */
    static final Duration START_TOLERANCE = Duration.ofMinutes(5);
    /** El cobro es por bloques de media hora: 1 h 10 min se cobra como 1 h 30 min. */
    static final long BILLING_BLOCK_MINUTES = 30;
    /** Paso con el que se revisa que el garaje esté abierto durante toda la franja. */
    private static final Duration SCHEDULE_STEP = Duration.ofMinutes(15);

    private final Clock clock;
    private final Duration minDuration;
    private final Duration maxDuration;
    private final Duration maxAdvance;
    private final Duration paymentTimeout;
    private final Duration holdBeforeStart;
    private final Duration checkInEarly;

    public ReservationPolicy(Clock clock,
                             @Value("${app.reservations.min-duration:PT30M}") Duration minDuration,
                             @Value("${app.reservations.max-duration:PT24H}") Duration maxDuration,
                             @Value("${app.reservations.max-advance:P30D}") Duration maxAdvance,
                             @Value("${app.reservations.payment-timeout:PT15M}") Duration paymentTimeout,
                             @Value("${app.reservations.hold-before-start:PT30M}") Duration holdBeforeStart,
                             @Value("${app.reservations.check-in-early:PT30M}") Duration checkInEarly) {
        this.clock = clock;
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.maxAdvance = maxAdvance;
        this.paymentTimeout = paymentTimeout;
        this.holdBeforeStart = holdBeforeStart;
        this.checkInEarly = checkInEarly;
    }

    public Instant now() {
        return Instant.now(clock);
    }

    /** Valida la franja contra el reloj y las reglas de duración. Lanza 400 con un mensaje claro. */
    public void validateSlot(Instant start, Instant end) {
        Instant now = now();
        if (!end.isAfter(start)) {
            throw new ApiException("La hora de fin debe ser posterior a la de inicio");
        }
        if (start.isBefore(now.minus(START_TOLERANCE))) {
            throw new ApiException("La franja no puede empezar en el pasado");
        }
        if (start.isAfter(now.plus(maxAdvance))) {
            throw new ApiException("Solo se puede reservar con máximo " + maxAdvance.toDays() + " días de anticipación");
        }
        Duration duration = Duration.between(start, end);
        if (duration.compareTo(minDuration) < 0) {
            throw new ApiException("La reserva debe durar al menos " + minDuration.toMinutes() + " minutos");
        }
        if (duration.compareTo(maxDuration) > 0) {
            throw new ApiException("La reserva no puede durar más de " + maxDuration.toHours() + " horas");
        }
    }

    /** El garaje debe estar activo y abierto durante toda la franja (incluye horarios nocturnos). */
    public void validateGarage(Garage garage, Instant start, Instant end) {
        if (garage.getStatus() != GarageStatus.ACTIVE) {
            throw new ConflictException("El garaje no está recibiendo reservas en este momento");
        }
        if (!isOpenDuring(garage, start, end)) {
            throw new ApiException("El garaje no está abierto durante toda la franja seleccionada");
        }
    }

    boolean isOpenDuring(Garage garage, Instant start, Instant end) {
        if (garage.isOpen24Hours()) {
            return true;
        }
        ZoneId zone = clock.getZone();
        for (Instant t = start; t.isBefore(end); t = t.plus(SCHEDULE_STEP)) {
            if (!OpeningHours.isOpenAt(garage, LocalTime.ofInstant(t, zone))) {
                return false;
            }
        }
        // El último instante de la franja (justo antes del fin) también debe estar dentro del horario.
        return OpeningHours.isOpenAt(garage, LocalTime.ofInstant(end.minusSeconds(1), zone));
    }

    /** Precio de la franja: bloques de 30 minutos a la mitad de la tarifa por hora. */
    public BigDecimal price(BigDecimal pricePerHour, Instant start, Instant end) {
        return priceForMinutes(pricePerHour, Duration.between(start, end).toMinutes());
    }

    public BigDecimal priceForMinutes(BigDecimal pricePerHour, long minutes) {
        if (minutes <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long blocks = (minutes + BILLING_BLOCK_MINUTES - 1) / BILLING_BLOCK_MINUTES;
        return pricePerHour.multiply(BigDecimal.valueOf(blocks))
                .divide(BigDecimal.valueOf(60 / BILLING_BLOCK_MINUTES), 2, RoundingMode.HALF_UP);
    }

    /** La plaza se aparta (estado RESERVED) cuando falta menos de holdBeforeStart para el inicio. */
    public boolean shouldHoldSpot(Instant start) {
        return !start.isAfter(now().plus(holdBeforeStart));
    }
}
