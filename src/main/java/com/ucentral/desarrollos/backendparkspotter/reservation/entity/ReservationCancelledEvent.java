package com.ucentral.desarrollos.backendparkspotter.reservation.entity;

import java.util.UUID;

/** Se publica dentro de la transacción al cancelar una reserva (el módulo de pagos marca el reembolso). */
public record ReservationCancelledEvent(UUID reservationId) {
}
