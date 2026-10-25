package com.ucentral.desarrollos.backendparkspotter.payment.entity;

public enum PaymentStatus {
    /** Creado; el conductor todavía no termina en la pasarela. */
    PENDING,
    APPROVED,
    DECLINED,
    VOIDED,
    ERROR,
    /** Aprobado pero la reserva se canceló o venció: hay que devolver el dinero (proceso manual en el panel de la pasarela). */
    REFUND_PENDING
}
