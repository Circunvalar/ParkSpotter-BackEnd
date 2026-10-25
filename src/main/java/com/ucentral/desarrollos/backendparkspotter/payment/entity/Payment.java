package com.ucentral.desarrollos.backendparkspotter.payment.entity;

import com.ucentral.desarrollos.backendparkspotter.reservation.entity.Reservation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Intento de pago de una reserva en la pasarela (Sprint 05, PB-21). Una reserva puede tener varios intentos. */
@Getter
@Setter
@Entity
@Table(name = "payments", indexes = @Index(name = "idx_payments_reservation", columnList = "reservation_id"))
public class Payment {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Reservation reservation;

    /** Referencia única que se envía a la pasarela y vuelve en el webhook. */
    @Column(nullable = false, unique = true, length = 60)
    private String reference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_in_cents", nullable = false)
    private Long amountInCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_transaction_id", length = 100)
    private String providerTransactionId;

    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    @Column(name = "checkout_url", length = 2000)
    private String checkoutUrl;

    @Column(name = "redirect_url", length = 500)
    private String redirectUrl;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;
}
