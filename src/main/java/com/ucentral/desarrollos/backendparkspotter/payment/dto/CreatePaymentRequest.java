package com.ucentral.desarrollos.backendparkspotter.payment.dto;

import jakarta.validation.constraints.Size;

/**
 * @param redirectUrl a dónde vuelve el conductor después de pagar (página de la web o deep link de la app).
 *                    Debe empezar por uno de los prefijos de app.payments.allowed-redirect-prefixes.
 */
public record CreatePaymentRequest(@Size(max = 500) String redirectUrl) {
}
