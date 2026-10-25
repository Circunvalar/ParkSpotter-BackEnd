package com.ucentral.desarrollos.backendparkspotter.garageManagement.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** @param notes observaciones para el dueño; recomendable al rechazar */
public record VerificationDecisionRequest(
        @NotNull Boolean approved,
        @Size(max = 500) String notes
) {
}
