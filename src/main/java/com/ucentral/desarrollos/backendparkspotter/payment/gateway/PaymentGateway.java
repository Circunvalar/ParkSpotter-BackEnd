package com.ucentral.desarrollos.backendparkspotter.payment.gateway;

import com.ucentral.desarrollos.backendparkspotter.payment.entity.Payment;

import java.util.Map;

/**
 * Pasarela de pagos. Implementaciones: Wompi (sandbox o producción, según las llaves) y una pasarela
 * simulada para desarrollo local y pruebas (app.payments.provider=sandbox).
 */
public interface PaymentGateway {

    String provider();

    /** Prepara el checkout del pago (URL a la que el front envía al conductor y datos del widget). */
    CheckoutSession createCheckout(Payment payment, String customerEmail);

    /**
     * Verifica la firma del webhook y lo traduce a un evento normalizado.
     *
     * @throws InvalidWebhookException si la firma no es válida o el cuerpo no tiene el formato esperado
     */
    WebhookEvent parseWebhook(String rawBody, Map<String, String> headers);

    /** Datos para abrir el checkout. publicKey y integritySignature solo aplican al widget de Wompi. */
    record CheckoutSession(String checkoutUrl, String publicKey, String integritySignature) {
    }

    /**
     * @param eventKey clave de idempotencia (misma transacción y mismo estado = mismo evento)
     * @param status   estado de la transacción ya traducido a los estados de ParkSpotter
     */
    record WebhookEvent(String eventKey, String reference, String transactionId,
                        com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus status,
                        long amountInCents, String paymentMethod) {
    }

    class InvalidWebhookException extends RuntimeException {
        public InvalidWebhookException(String message) {
            super(message);
        }
    }
}
