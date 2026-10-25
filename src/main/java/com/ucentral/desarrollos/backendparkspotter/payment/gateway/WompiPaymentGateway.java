package com.ucentral.desarrollos.backendparkspotter.payment.gateway;

import com.ucentral.desarrollos.backendparkspotter.payment.entity.Payment;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus;
import com.ucentral.desarrollos.backendparkspotter.shared.security.SecureTokens;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Integración con Wompi (pasarela colombiana; ambiente sandbox gratuito con llaves pub_test/prv_test).
 *
 * - Checkout: Web Checkout de Wompi con firma de integridad SHA-256(referencia + monto en centavos + moneda + secreto de integridad).
 * - Webhook: evento transaction.updated firmado; checksum = SHA-256(valores de signature.properties + timestamp + secreto de eventos).
 *
 * Documentación: https://docs.wompi.co/docs/colombia/widget-checkout-web/ y https://docs.wompi.co/docs/colombia/eventos/
 */
public class WompiPaymentGateway implements PaymentGateway {

    static final String CHECKOUT_URL = "https://checkout.wompi.co/p/";

    private final ObjectMapper objectMapper;
    private final String publicKey;
    private final String integritySecret;
    private final String eventsSecret;

    public WompiPaymentGateway(ObjectMapper objectMapper, String publicKey, String integritySecret, String eventsSecret) {
        this.objectMapper = objectMapper;
        this.publicKey = publicKey;
        this.integritySecret = integritySecret;
        this.eventsSecret = eventsSecret;
    }

    @Override
    public String provider() {
        return "WOMPI";
    }

    @Override
    public CheckoutSession createCheckout(Payment payment, String customerEmail) {
        String signature = SecureTokens.sha256Hex(payment.getReference() + payment.getAmountInCents()
                + payment.getCurrency() + integritySecret);
        UriComponentsBuilder url = UriComponentsBuilder.fromUriString(CHECKOUT_URL)
                .queryParam("public-key", publicKey)
                .queryParam("currency", payment.getCurrency())
                .queryParam("amount-in-cents", payment.getAmountInCents())
                .queryParam("reference", payment.getReference())
                .queryParam("signature:integrity", signature);
        if (payment.getRedirectUrl() != null) {
            url.queryParam("redirect-url", payment.getRedirectUrl());
        }
        if (customerEmail != null) {
            url.queryParam("customer-data:email", customerEmail);
        }
        return new CheckoutSession(url.encode().build().toUriString(), publicKey, signature);
    }

    @Override
    public WebhookEvent parseWebhook(String rawBody, Map<String, String> headers) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (JacksonException ex) {
            throw new InvalidWebhookException("Cuerpo del webhook inválido");
        }
        JsonNode signature = root.path("signature");
        JsonNode properties = signature.path("properties");
        String checksum = signature.path("checksum").asString("");
        if (!properties.isArray() || checksum.isBlank() || !root.hasNonNull("timestamp")) {
            throw new InvalidWebhookException("El webhook no trae firma");
        }
        StringBuilder toSign = new StringBuilder();
        for (JsonNode property : properties) {
            toSign.append(valueAt(root.path("data"), property.asString()));
        }
        toSign.append(root.get("timestamp").asString()).append(eventsSecret);
        if (!SecureTokens.constantTimeEquals(SecureTokens.sha256Hex(toSign.toString()), checksum.toLowerCase())) {
            throw new InvalidWebhookException("Firma del webhook inválida");
        }
        JsonNode transaction = root.path("data").path("transaction");
        String transactionId = transaction.path("id").asString("");
        String wompiStatus = transaction.path("status").asString("");
        if (transactionId.isBlank() || wompiStatus.isBlank()) {
            throw new InvalidWebhookException("El webhook no trae la transacción");
        }
        return new WebhookEvent(
                provider() + ":" + transactionId + ":" + wompiStatus,
                transaction.path("reference").asString(null),
                transactionId,
                mapStatus(wompiStatus),
                transaction.path("amount_in_cents").asLong(-1),
                transaction.path("payment_method_type").asString(null));
    }

    /** Valor de una propiedad con puntos (ej. "transaction.amount_in_cents") dentro de data. */
    private static String valueAt(JsonNode data, String path) {
        JsonNode node = data;
        for (String part : path.split("\\.")) {
            node = node.path(part);
        }
        return node.isMissingNode() || node.isNull() ? "" : node.asString();
    }

    static PaymentStatus mapStatus(String wompiStatus) {
        return switch (wompiStatus) {
            case "APPROVED" -> PaymentStatus.APPROVED;
            case "DECLINED" -> PaymentStatus.DECLINED;
            case "VOIDED" -> PaymentStatus.VOIDED;
            case "PENDING" -> PaymentStatus.PENDING;
            default -> PaymentStatus.ERROR;
        };
    }
}
