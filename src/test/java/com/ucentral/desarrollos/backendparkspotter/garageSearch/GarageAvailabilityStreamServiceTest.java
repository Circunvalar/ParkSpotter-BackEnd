package com.ucentral.desarrollos.backendparkspotter.garageSearch;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageAvailability;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.GarageStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.event.GarageAvailabilityChangedEvent;
import com.ucentral.desarrollos.backendparkspotter.garageSearch.realtime.GarageAvailabilityStreamService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class GarageAvailabilityStreamServiceTest {

    private final GarageAvailabilityStreamService service = new GarageAvailabilityStreamService(Duration.ofMinutes(1));

    @Test
    void subscriptions_AreTrackedPerGarageAndGlobally() {
        UUID garageId = UUID.randomUUID();

        SseEmitter perGarage = service.subscribe(garageId, snapshot(garageId, 5));
        SseEmitter global = service.subscribeAll();

        assertThat(perGarage).isNotNull();
        assertThat(global).isNotNull();
        assertThat(service.subscriberCount()).isEqualTo(2);
    }

    @Test
    void publishingEvents_ForGaragesWithAndWithoutSubscribers_DoesNotFail() {
        UUID garageId = UUID.randomUUID();
        service.subscribe(garageId, snapshot(garageId, 5));
        service.subscribeAll();

        assertThatCode(() -> {
            service.onAvailabilityChanged(new GarageAvailabilityChangedEvent(snapshot(garageId, 4)));
            service.onAvailabilityChanged(new GarageAvailabilityChangedEvent(snapshot(UUID.randomUUID(), 1)));
            service.heartbeat();
        }).doesNotThrowAnyException();
    }

    private static GarageAvailabilityResponse snapshot(UUID garageId, int available) {
        return new GarageAvailabilityResponse(garageId, GarageStatus.ACTIVE, GarageAvailability.AVAILABLE, true,
                10, available, 10 - available, 0, 0, Instant.now());
    }
}
