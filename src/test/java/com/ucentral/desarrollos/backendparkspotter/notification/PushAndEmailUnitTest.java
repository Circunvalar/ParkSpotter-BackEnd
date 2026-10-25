package com.ucentral.desarrollos.backendparkspotter.notification;

import com.ucentral.desarrollos.backendparkspotter.notification.service.EmailSender;
import com.ucentral.desarrollos.backendparkspotter.notification.service.FcmPushSender;
import com.ucentral.desarrollos.backendparkspotter.notification.service.GoogleAccessTokenProvider;
import com.ucentral.desarrollos.backendparkspotter.notification.service.PushConfig;
import com.ucentral.desarrollos.backendparkspotter.notification.service.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Push por FCM (con un servidor HTTP simulado) y correo, sin red ni credenciales reales. */
class PushAndEmailUnitTest {

    static final String TOKEN_URI = "https://oauth2.example.test/token";
    static final String SEND_URI = "https://fcm.googleapis.com/v1/projects/demo/messages:send";
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    static String pem;

    MockRestServiceServer server;
    RestClient restClient;

    @BeforeEach
    void setUp() throws Exception {
        if (pem == null) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            pem = "-----BEGIN PRIVATE KEY-----\\n"
                    + Base64.getEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                    + "\\n-----END PRIVATE KEY-----";
        }
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    private FcmPushSender sender() {
        return new FcmPushSender(restClient, new GoogleAccessTokenProvider(restClient, "svc@demo.iam", pem, TOKEN_URI, CLOCK), "demo");
    }

    private void expectToken(String body) {
        server.expect(once(), requestTo(TOKEN_URI)).andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void send_GetsTokenOnce_AndReusesIt() {
        FcmPushSender sender = sender();
        expectToken("{\"access_token\":\"ya29.abc\",\"expires_in\":3600}");
        server.expect(requestTo(SEND_URI)).andExpect(header("Authorization", "Bearer ya29.abc")).andRespond(withSuccess());
        server.expect(requestTo(SEND_URI)).andExpect(header("Authorization", "Bearer ya29.abc")).andRespond(withSuccess());

        assertThat(sender.send("device", "Hola", "Cuerpo", Map.of("type", "X"))).isEqualTo(PushSender.Result.SENT);
        assertThat(sender.send("device", "Hola", "Cuerpo", Map.of())).isEqualTo(PushSender.Result.SENT);
        server.verify();
    }

    @Test
    void send_MapsErrors() {
        FcmPushSender sender = sender();
        expectToken("{\"access_token\":\"t\"}");
        server.expect(requestTo(SEND_URI)).andRespond(withResourceNotFound());
        server.expect(requestTo(SEND_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .body("{\"error\":{\"status\":\"INVALID_ARGUMENT\"}}").contentType(MediaType.APPLICATION_JSON));
        server.expect(requestTo(SEND_URI)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("otro"));
        server.expect(requestTo(SEND_URI)).andRespond(withServerError());

        assertThat(sender.send("d", "t", "b", Map.of())).isEqualTo(PushSender.Result.INVALID_TOKEN);
        assertThat(sender.send("d", "t", "b", Map.of())).isEqualTo(PushSender.Result.INVALID_TOKEN);
        assertThat(sender.send("d", "t", "b", Map.of())).isEqualTo(PushSender.Result.FAILED);
        assertThat(sender.send("d", "t", "b", Map.of())).isEqualTo(PushSender.Result.FAILED);
    }

    @Test
    void send_FailsWhenGoogleReturnsNoToken() {
        FcmPushSender sender = sender();
        expectToken("{\"error\":\"invalid_grant\"}");
        assertThat(sender.send("d", "t", "b", Map.of())).isEqualTo(PushSender.Result.FAILED);
    }

    @Test
    void send_FailsWhenTokenEndpointIsDown() {
        FcmPushSender sender = sender();
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());
        assertThat(sender.send("d", "t", "b", Map.of())).isEqualTo(PushSender.Result.FAILED);
    }

    @Test
    void invalidPrivateKey_IsRejected() {
        assertThatThrownBy(() -> new GoogleAccessTokenProvider(restClient, "a", "no es una clave", TOKEN_URI, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pushConfig_DisabledUnlessFullyConfigured() {
        PushConfig config = new PushConfig();
        assertThat(config.pushSender(false, "p", "e", pem, TOKEN_URI, CLOCK).send("t", "a", "b", Map.of()))
                .isEqualTo(PushSender.Result.DISABLED);
        assertThat(config.pushSender(true, "", "e", pem, TOKEN_URI, CLOCK)).isNotInstanceOf(FcmPushSender.class);
        assertThat(config.pushSender(true, "p", "", pem, TOKEN_URI, CLOCK)).isNotInstanceOf(FcmPushSender.class);
        assertThat(config.pushSender(true, "p", "e", "", TOKEN_URI, CLOCK)).isNotInstanceOf(FcmPushSender.class);
        assertThat(config.pushSender(true, "p", "e", pem, TOKEN_URI, CLOCK)).isInstanceOf(FcmPushSender.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailSender_DisabledMissingSuccessAndFailure() {
        JavaMailSender mail = mock(JavaMailSender.class);
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mail);

        assertThat(new EmailSender(provider, false, "a@b.co").send("x@y.co", "s", "t")).isFalse();
        assertThat(new EmailSender(mock(ObjectProvider.class), true, "a@b.co").send("x@y.co", "s", "t")).isFalse();
        assertThat(new EmailSender(provider, true, "a@b.co").send("x@y.co", "s", "t")).isTrue();
        verify(mail).send(any(SimpleMailMessage.class));

        doThrow(new MailSendException("caído")).when(mail).send(any(SimpleMailMessage.class));
        assertThat(new EmailSender(provider, true, "a@b.co").send("x@y.co", "s", "t")).isFalse();
    }
}
