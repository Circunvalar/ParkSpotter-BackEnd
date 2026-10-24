package com.ucentral.desarrollos.backendparkspotter.payment;

import com.ucentral.desarrollos.backendparkspotter.payment.entity.Payment;
import com.ucentral.desarrollos.backendparkspotter.payment.entity.PaymentStatus;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.PaymentGateway;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.PaymentGatewayConfig;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.SandboxPaymentGateway;
import com.ucentral.desarrollos.backendparkspotter.payment.gateway.WompiPaymentGateway;
import com.ucentral.desarrollos.backendparkspotter.shared.security.SecureTokens;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WompiPaymentGatewayTest {

    final ObjectMapper mapper = JsonMapper.builder().build();
    final WompiPaymentGateway gateway = new WompiPaymentGateway(mapper, "pub_test_x", "integrity", WompiWebhookApiTest.EVENTS_SECRET);

    @Test
    void createCheckout_SignsReference_WithOptionalParams() {
        Payment payment = new Payment();
        payment.setReference("PS-ABC123-1");
        payment.setAmountInCents(500000L);
        payment.setCurrency("COP");

        PaymentGateway.CheckoutSession bare = gateway.createCheckout(payment, null);
        assertThat(bare.integritySignature()).isEqualTo(SecureTokens.sha256Hex("PS-ABC123-1500000COPintegrity"));
        assertThat(bare.checkoutUrl()).doesNotContain("redirect-url").doesNotContain("customer-data");

        payment.setRedirectUrl("parkspotter://pago");
        assertThat(gateway.createCheckout(payment, "a@b.co").checkoutUrl()).contains("redirect-url").contains("customer-data");
    }

    @Test
    void parseWebhook_ReadsTransaction() {
        PaymentGateway.WebhookEvent event = gateway.parseWebhook(WompiWebhookApiTest.event("t1", "VOIDED", "R-1", 10), Map.of());
        assertThat(event.status()).isEqualTo(PaymentStatus.VOIDED);
        assertThat(event.eventKey()).isEqualTo("WOMPI:t1:VOIDED");
        assertThat(event.amountInCents()).isEqualTo(10);
    }

    @Test
    void parseWebhook_RejectsMissingSignatureOrTransaction() {
        assertThatThrownBy(() -> gateway.parseWebhook("{\"data\":{}}", Map.of()))
                .isInstanceOf(PaymentGateway.InvalidWebhookException.class);
        assertThatThrownBy(() -> gateway.parseWebhook("{\"signature\":{\"properties\":[],\"checksum\":\"x\"}}", Map.of()))
                .isInstanceOf(PaymentGateway.InvalidWebhookException.class);
        String noTransaction = "{\"data\":{},\"signature\":{\"properties\":[\"transaction.id\"],\"checksum\":\""
                + SecureTokens.sha256Hex("1" + WompiWebhookApiTest.EVENTS_SECRET) + "\"},\"timestamp\":1}";
        assertThatThrownBy(() -> gateway.parseWebhook(noTransaction, Map.of()))
                .isInstanceOf(PaymentGateway.InvalidWebhookException.class)
                .hasMessageContaining("transacción");
    }

    @Test
    void statusMapping_UnknownIsError() {
        PaymentGateway.WebhookEvent pending = gateway.parseWebhook(WompiWebhookApiTest.event("t2", "PENDING", "R", 1), Map.of());
        PaymentGateway.WebhookEvent other = gateway.parseWebhook(WompiWebhookApiTest.event("t3", "RARO", "R", 1), Map.of());
        assertThat(pending.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(other.status()).isEqualTo(PaymentStatus.ERROR);
    }

    @Test
    void config_ChoosesGateway_AndRequiresWompiKeys() {
        PaymentGatewayConfig config = new PaymentGatewayConfig();
        assertThat(config.paymentGateway("sandbox", "", "", "", mapper)).isInstanceOf(SandboxPaymentGateway.class);
        assertThat(config.paymentGateway(" WOMPI ", "p", "i", "e", mapper)).isInstanceOf(WompiPaymentGateway.class);
        assertThatThrownBy(() -> config.paymentGateway("wompi", "", "i", "e", mapper)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.paymentGateway("wompi", "p", "", "e", mapper)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.paymentGateway("wompi", "p", "i", "", mapper)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sandbox_CheckoutWithoutRedirect_AndNoWebhooks() {
        SandboxPaymentGateway sandbox = new SandboxPaymentGateway();
        Payment payment = new Payment();
        payment.setReference("R-1");
        assertThat(sandbox.createCheckout(payment, null).checkoutUrl()).isEqualTo("about:blank");
        assertThatThrownBy(() -> sandbox.parseWebhook("{}", Map.of())).isInstanceOf(PaymentGateway.InvalidWebhookException.class);
    }
}
