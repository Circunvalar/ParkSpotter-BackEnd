package com.ucentral.desarrollos.backendparkspotter.garageSearch.realtime;

import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageAvailabilityResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.event.GarageAvailabilityChangedEvent;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado en tiempo real con Server-Sent Events (SSE). Web (EventSource) y Android (OkHttp SSE)
 * se suscriben y reciben un evento "availability" cada vez que cambia la disponibilidad,
 * sin tener que hacer polling.
 */
@Slf4j
@Service
public class GarageAvailabilityStreamService {

    public static final String EVENT_NAME = "availability";

    private final Map<UUID, Set<SseEmitter>> garageSubscribers = new ConcurrentHashMap<>();
    private final Set<SseEmitter> globalSubscribers = ConcurrentHashMap.newKeySet();
    private final long timeoutMillis;
    private final int maxSubscribers;

    public GarageAvailabilityStreamService(@Value("${app.garages.stream-timeout:PT30M}") Duration timeout,
                                           @Value("${app.garages.stream-max-subscribers:1000}") int maxSubscribers) {
        this.timeoutMillis = timeout.toMillis();
        this.maxSubscribers = maxSubscribers;
    }

    /** Suscripción a los cambios de todos los garajes (para refrescar marcadores del mapa). */
    public SseEmitter subscribeAll() {
        assertCapacity();
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        register(emitter, globalSubscribers);
        return emitter;
    }

    /** Suscripción a un garaje; envía de inmediato su disponibilidad actual. */
    public SseEmitter subscribe(UUID garageId, GarageAvailabilityResponse current) {
        assertCapacity();
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        Set<SseEmitter> subscribers = garageSubscribers.computeIfAbsent(garageId, id -> ConcurrentHashMap.newKeySet());
        register(emitter, subscribers);
        send(emitter, current, subscribers);
        return emitter;
    }

    /** Se ejecuta tras el commit: nunca se notifica un cambio que luego se revirtió. */
    @TransactionalEventListener(fallbackExecution = true)
    public void onAvailabilityChanged(GarageAvailabilityChangedEvent event) {
        GarageAvailabilityResponse snapshot = event.snapshot();
        Set<SseEmitter> subscribers = garageSubscribers.get(snapshot.garageId());
        if (subscribers != null) {
            subscribers.forEach(emitter -> send(emitter, snapshot, subscribers));
        }
        globalSubscribers.forEach(emitter -> send(emitter, snapshot, globalSubscribers));
    }

    /** Comentario periódico para que proxies y balanceadores no cierren conexiones inactivas. */
    @Scheduled(fixedDelayString = "${app.garages.stream-heartbeat:PT25S}")
    public void heartbeat() {
        globalSubscribers.forEach(emitter -> ping(emitter, globalSubscribers));
        garageSubscribers.values().forEach(subscribers -> subscribers.forEach(emitter -> ping(emitter, subscribers)));
    }

    public int subscriberCount() {
        return globalSubscribers.size() + garageSubscribers.values().stream().mapToInt(Set::size).sum();
    }

    /**
     * Cada conexión SSE ocupa recursos del servidor mientras está abierta: con el tope se evita
     * que muchas conexiones (o un cliente malicioso) agoten el servidor. El front debe reintentar luego.
     */
    private void assertCapacity() {
        if (subscriberCount() >= maxSubscribers) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Demasiadas conexiones en tiempo real abiertas, intenta de nuevo en unos segundos");
        }
    }

    private void register(SseEmitter emitter, Set<SseEmitter> subscribers) {
        subscribers.add(emitter);
        emitter.onCompletion(() -> subscribers.remove(emitter));
        emitter.onTimeout(() -> {
            subscribers.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> subscribers.remove(emitter));
    }

    private void send(SseEmitter emitter, GarageAvailabilityResponse snapshot, Set<SseEmitter> subscribers) {
        try {
            emitter.send(SseEmitter.event()
                    .name(EVENT_NAME)
                    .id(snapshot.garageId() + ":" + snapshot.timestamp().toEpochMilli())
                    .data(snapshot));
        } catch (IOException | IllegalStateException ex) {
            // Cliente desconectado: se descarta la suscripción.
            subscribers.remove(emitter);
            log.debug("Suscriptor SSE descartado: {}", ex.getMessage());
        }
    }

    private void ping(SseEmitter emitter, Set<SseEmitter> subscribers) {
        try {
            emitter.send(SseEmitter.event().comment("ping"));
        } catch (IOException | IllegalStateException ex) {
            subscribers.remove(emitter);
        }
    }
}
