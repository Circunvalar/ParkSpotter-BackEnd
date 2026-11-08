package com.ucentral.desarrollos.backendparkspotter.helpdesk.dto;

import com.ucentral.desarrollos.backendparkspotter.helpdesk.entity.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record TicketStatusRequest(@NotNull TicketStatus status) {
}
