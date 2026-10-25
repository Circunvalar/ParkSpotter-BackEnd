package com.ucentral.desarrollos.backendparkspotter.notification.dto;

import com.ucentral.desarrollos.backendparkspotter.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String body,
        UUID referenceId,
        boolean read,
        Instant readAt,
        Instant createdAt
) {
}
