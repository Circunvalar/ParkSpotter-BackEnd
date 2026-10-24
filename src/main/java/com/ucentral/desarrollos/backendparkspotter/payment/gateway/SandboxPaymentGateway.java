package com.ucentral.desarrollos.backendparkspotter.payment.gateway;

import com.ucentral.desarrollos.backendparkspotter.payment.entity.Payment;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Pasarela simulada para desarrollo local, la CI y demostraciones sin llaves de Wompi.
 * El checkout lleva directo a la URL de retorno; el resultado se simula con
 * POST /api/v1/payments/{id}/sandbox-result (no hay webhook).
 */
public class SandboxPaymentGateway implements PaymentGateway {

    @Override
    public String provider() {
        return "SANDBOX";
    }

    @Override
    public CheckoutSession createCheckout(Payment payment, String customerEmail) {
        if (payment.getRedirectUrl() == null) {
            return new CheckoutSession("about:blank", null, null);
        }
        String url = UriComponentsBuilder.fromUriString(payment.getRedirectUrl())
                .queryParam("reference", payment.getReference())
                .queryParam("sandbox", true)
                .build().toUriString();
        return new CheckoutSession(url, null, null);
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        throw new InvalidWebhookException("La pasarela simulada no recibe webhooks");
    }
}
