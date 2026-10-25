package com.ucentral.desarrollos.backendparkspotter.notification.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.notification.dto.DeviceTokenRequest;
import com.ucentral.desarrollos.backendparkspotter.notification.dto.NotificationResponse;
import com.ucentral.desarrollos.backendparkspotter.notification.dto.UnreadCountResponse;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.DeviceToken;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.Notification;
import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;
import com.ucentral.desarrollos.backendparkspotter.notification.repository.DeviceTokenRepository;
import com.ucentral.desarrollos.backendparkspotter.notification.repository.NotificationRepository;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Notificaciones del sistema (Sprint 05, PB-23): se guardan para la bandeja de la web y la app,
 * y se envían por push (Firebase) y opcionalmente por correo.
 *
 * El registro en la bandeja ocurre dentro de la transacción del caso de uso (si este falla, no queda
 * notificación); el push y el correo se envían solo cuando la transacción se confirma.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushSender pushSender;
    private final EmailSender emailSender;
    private final Clock clock;

    // ---------- envío ----------

    @Transactional
    public void notify(UserAccount user, NotificationType type, String title, String body, UUID referenceId, boolean alsoEmail) {
        if (user == null || user.getDeletedAt() != null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(truncate(title, 150));
        notification.setBody(truncate(body, 500));
        notification.setReferenceId(referenceId);
        notificationRepository.save(notification);

        List<String> tokens = deviceTokenRepository.findByUserId(user.getId()).stream().map(DeviceToken::getToken).toList();
        String email = user.getEmail();
        Map<String, String> data = new HashMap<>();
        data.put("type", type.name());
        if (referenceId != null) {
            data.put("referenceId", referenceId.toString());
        }
        afterCommit(() -> {
            // Cada envío por separado: un dispositivo que falla no impide avisar a los demás.
            tokens.forEach(token -> runSafely(() -> sendPush(token, title, body, data)));
            if (alsoEmail) {
                emailSender.send(email, title, body + "\n\n— ParkSpotter");
            }
        });
    }

    private void sendPush(String token, String title, String body, Map<String, String> data) {
        if (pushSender.send(token, title, body, data) == PushSender.Result.INVALID_TOKEN) {
            deviceTokenRepository.deleteByToken(token);
        }
    }

    /** Ejecuta la acción al confirmar la transacción actual, o de inmediato si no hay transacción. */
    static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
        } else {
            runSafely(action);
        }
    }

    private static void runSafely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            // Un fallo de push o correo nunca debe tumbar el caso de uso que ya se confirmó.
            log.warn("Falló el envío externo de una notificación: {}", ex.getMessage());
        }
    }

    // ---------- bandeja ----------

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UserAccount user, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(user.getId(), pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        return PageResponse.of(result, NotificationService::toResponse);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UserAccount user) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(user.getId()));
    }

    @Transactional
    public NotificationResponse markRead(UUID id, UserAccount user) {
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new EntityNotFoundException("Notificación no encontrada"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now(clock));
        }
        return toResponse(notification);
    }

    @Transactional
    public void markAllRead(UserAccount user) {
        notificationRepository.markAllRead(user.getId(), Instant.now(clock));
    }

    // ---------- dispositivos ----------

    /** Registra (o reasigna al usuario actual) el token FCM del dispositivo. Idempotente. */
    @Transactional
    public void registerDevice(DeviceTokenRequest request, UserAccount user) {
        DeviceToken device = deviceTokenRepository.findByToken(request.token()).orElseGet(DeviceToken::new);
        device.setToken(request.token());
        device.setPlatform(request.platform());
        device.setUser(user);
        deviceTokenRepository.save(device);
    }

    @Transactional
    public void unregisterDevice(String token, UserAccount user) {
        deviceTokenRepository.deleteByTokenAndUserId(token, user.getId());
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReferenceId(),
                notification.getReadAt() != null,
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
