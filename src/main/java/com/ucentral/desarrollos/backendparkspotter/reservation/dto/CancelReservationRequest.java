package com.ucentral.desarrollos.backendparkspotter.reservation.dto;

import jakarta.validation.constraints.Size;

public record CancelReservationRequest(@Size(max = 300) String reason) {
}
