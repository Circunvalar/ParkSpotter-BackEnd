package com.ucentral.desarrollos.backendparkspotter.helpdesk.dto;

import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketCategory;
import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Caso de soporte. En los listados {@code messages} viene vacío; en el detalle trae la conversación.
 *
 * @param userEmail solo para soporte (administradores); al usuario le llega su propio correo
 */
public record TicketResponse(
        UUID id,
        String subject,
        TicketCategory category,
        TicketStatus status,
        UUID reservationId,
        UUID userId,
        String userEmail,
        int messageCount,
        List<Message> messages,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt
) {

    /** @param authorName "Soporte ParkSpotter" para respuestas de soporte; el primer nombre del usuario en las suyas */
    public record Message(UUID id, boolean fromStaff, String authorName, String body, Instant createdAt) {
    }
}
