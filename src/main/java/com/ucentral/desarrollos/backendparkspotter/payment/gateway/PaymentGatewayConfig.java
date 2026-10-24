package com.ucentral.desarrollos.backendparkspotter.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Elige la pasarela con app.payments.provider: "sandbox" (por defecto, simulada) o "wompi"
 * (llaves en WOMPI_PUBLIC_KEY, WOMPI_INTEGRITY_SECRET y WOMPI_EVENTS_SECRET; las de prueba empiezan por pub_test).
 */
@Configuration
public class PaymentGatewayConfig {

    @Bean
    public PaymentGateway paymentGateway(@Value("${app.payments.provider:sandbox}") String provider,
                                         @Value("${app.payments.wompi.public-key:}") String publicKey,
                                         @Value("${app.payments.wompi.integrity-secret:}") String integritySecret,
                                         @Value("${app.payments.wompi.events-secret:}") String eventsSecret,
                                         ObjectMapper objectMapper) {
        if ("wompi".equalsIgnoreCase(provider.trim())) {
            if (publicKey.isBlank() || integritySecret.isBlank() || eventsSecret.isBlank()) {
                throw new IllegalStateException("app.payments.provider=wompi requiere public-key, integrity-secret y events-secret");
            }
            return new WompiPaymentGateway(objectMapper, publicKey, integritySecret, eventsSecret);
        }
        return new SandboxPaymentGateway();
    }
}
