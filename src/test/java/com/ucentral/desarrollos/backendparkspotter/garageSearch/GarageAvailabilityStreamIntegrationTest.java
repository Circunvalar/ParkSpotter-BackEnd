package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.event.GarageAvailabilityChangedEvent;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageService;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.realtime.GarageAvailabilityStreamService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Stream SSE contra un servidor HTTP real (Tomcat en puerto aleatorio): un cliente que se desconecta
 * o cuya conexión vence debe liberar su suscripción (sin fugas de memoria ni de hilos).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.garages.stream-timeout=PT2S")
class GarageAvailabilityStreamIntegrationTest {

    @Value("${local.server.port}") int port;
    @Autowired GarageAvailabilityStreamService streamService;
    @Autowired GarageService garageService;
    @Autowired GarageRepository garageRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;

    GarageResponse garage;

    @BeforeEach
    void setUp() {
        GarageSearchTestData.cleanDatabase(garageRepository, userRepository, roleRepository);
        UserAccount owner = GarageSearchTestData.user(userRepository, roleRepository, "sse-owner@parkspotter.co");
        garage = GarageSearchTestData.centro(garageService, owner);
    }

    @AfterEach
    void tearDown() {
        garageRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void clientThatDisconnects_IsRemovedFromSubscribers() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = openStream(client);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(type -> assertThat(type).contains("text/event-stream"));
            assertThat(readUntilEvent(response.body())).contains("\"garageId\":\"" + garage.id() + "\"");
            assertThat(streamService.subscriberCount()).isEqualTo(1);

            response.body().close();
            client.shutdownNow();
        }

        // El servidor detecta la desconexión al intentar escribir (heartbeat) y libera la suscripción
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            streamService.heartbeat();
            assertThat(streamService.subscriberCount()).isZero();
        });
    }

    @Test
    void availabilityEventToDisconnectedClient_RemovesTheSubscription() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = openStream(client);
            readUntilEvent(response.body());
            response.body().close();
            client.shutdownNow();
        }
        GarageAvailabilityResponse snapshot = new GarageAvailabilityResponse(garage.id(), GarageStatus.ACTIVE,
                GarageAvailability.AVAILABLE, true, 50, 49, 1, 0, 0, Instant.now());

        // Un cambio de disponibilidad para un cliente que ya no está: el envío falla y se libera la suscripción
        await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            streamService.onAvailabilityChanged(new GarageAvailabilityChangedEvent(snapshot));
            assertThat(streamService.subscriberCount()).isZero();
        });
    }

    @Test
    void streamThatTimesOut_IsRemovedFromSubscribers() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<InputStream> response = openStream(client);
            readUntilEvent(response.body());
            assertThat(streamService.subscriberCount()).isEqualTo(1);

            // stream-timeout=PT2S en esta prueba
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(streamService.subscriberCount()).isZero());
            response.body().close();
        }
    }

    private HttpResponse<InputStream> openStream(HttpClient client) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                        URI.create("http://localhost:" + port + "/api/v1/garages/" + garage.id() + "/availability/stream"))
                .header("Accept", "text/event-stream")
                .timeout(Duration.ofSeconds(10))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofInputStream());
    }

    /** Lee el stream hasta recibir el primer evento "availability" y devuelve su línea de datos. */
    private static String readUntilEvent(InputStream body) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        boolean eventSeen = false;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("event:availability")) {
                eventSeen = true;
            } else if (eventSeen && line.startsWith("data:")) {
                return line;
            }
        }
        throw new AssertionError("El stream terminó sin enviar el evento inicial");
    }
}
