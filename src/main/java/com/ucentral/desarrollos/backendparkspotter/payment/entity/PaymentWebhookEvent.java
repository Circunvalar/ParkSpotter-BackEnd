package com.ucentral.desarrollos.backendparkspotter.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Eventos de webhook ya procesados (idempotencia, T5-04): la pasarela reintenta los envíos, y un mismo
 * evento (transacción + estado) solo debe aplicarse una vez.
 */
@Getter
@Setter
@Entity
@Table(name = "payment_webhook_events")
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_key", nullable = false, unique = true, length = 200)
    private String eventKey;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(length = 60)
    private String reference;

    @Column(nullable = false, length = 20)
    private String status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant receivedAt;
}
