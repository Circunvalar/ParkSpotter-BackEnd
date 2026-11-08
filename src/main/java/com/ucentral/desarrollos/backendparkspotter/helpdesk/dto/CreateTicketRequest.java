package com.ucentral.desarrollos.backendparkspotter.helpdesk.dto;

import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateTicketRequest(
        @NotBlank @Size(min = 5, max = 150) String subject,
        @NotNull TicketCategory category,
        @NotBlank @Size(min = 10, max = 2000) String message,
        UUID reservationId
) {
}
