package com.ucentral.desarrollos.backendparkspotter.reservation.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de una reserva (Sprint 04-05):
 *
 * PENDING_PAYMENT → CONFIRMED (pago aprobado) → CHECKED_IN (entrada) → COMPLETED (salida)
 * PENDING_PAYMENT → EXPIRED (no se pagó a tiempo)
 * PENDING_PAYMENT | CONFIRMED → CANCELLED (conductor, dueño o administrador)
 * CONFIRMED → NO_SHOW (terminó la franja sin registrar entrada)
 */
public enum ReservationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CHECKED_IN,
    COMPLETED,
    CANCELLED,
    EXPIRED,
    NO_SHOW;

    /** Reservas que ocupan o van a ocupar una plaza. */
    public static final Set<ReservationStatus> ACTIVE = EnumSet.of(PENDING_PAYMENT, CONFIRMED, CHECKED_IN);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }

    public boolean isCancellable() {
        return this == PENDING_PAYMENT || this == CONFIRMED;
    }
}
