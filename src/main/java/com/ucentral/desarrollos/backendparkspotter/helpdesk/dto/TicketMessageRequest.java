package com.ucentral.desarrollos.backendparkspotter.helpdesk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TicketMessageRequest(@NotBlank @Size(max = 2000) String body) {
}
