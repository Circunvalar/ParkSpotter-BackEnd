package com.ucentral.desarrollos.backendparkspotter.payment.dto;

import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param checkoutUrl        URL de la pasarela: abrirla en el navegador (web) o en una Custom Tab (Android)
 * @param publicKey          solo Wompi: llave pública para el widget embebido (alternativa a checkoutUrl)
 * @param integritySignature solo Wompi: firma de integridad para el widget embebido
 */
public record PaymentResponse(
        UUID id,
        UUID reservationId,
        String reference,
        BigDecimal amount,
        long amountInCents,
        String currency,
        PaymentStatus status,
        String provider,
        String paymentMethod,
        String checkoutUrl,
        String redirectUrl,
        String publicKey,
        String integritySignature,
        Instant createdAt,
        Instant approvedAt
) {
}
