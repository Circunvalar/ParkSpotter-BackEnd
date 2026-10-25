package com.ucentral.desarrollos.backendparkspotter.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Tareas periódicas de reservas (cada app.reservations.scheduler-interval, por defecto 1 minuto):
 * vencer las no pagadas (T4-06), apartar la plaza de las que están por empezar y marcar las no presentadas.
 * Cada reserva se procesa en su propia transacción: un error en una no detiene las demás.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reservations.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ReservationJobs {

    private final ReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.reservations.scheduler-interval:PT1M}", initialDelayString = "PT30S")
    public void run() {
        int expired = process(reservationService.findExpiredUnpaidIds(), reservationService::expire);
        int held = process(reservationService.findToHoldIds(), reservationService::hold);
        int noShows = process(reservationService.findNoShowIds(), reservationService::markNoShow);
        if (expired + held + noShows > 0) {
            log.info("Reservas: {} vencidas, {} plazas apartadas, {} no presentadas", expired, held, noShows);
        }
    }

    static int process(List<UUID> ids, Predicate<UUID> action) {
        int done = 0;
        for (UUID id : ids) {
            try {
                if (action.test(id)) {
                    done++;
                }
            } catch (RuntimeException ex) {
                log.warn("No se pudo procesar la reserva {}: {}", id, ex.getMessage());
            }
        }
        return done;
    }
}
