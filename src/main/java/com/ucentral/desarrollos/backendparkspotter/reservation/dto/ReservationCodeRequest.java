package com.ucentral.desarrollos.backendparkspotter.reservation.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Código de reserva que el conductor muestra en la entrada (texto o QR con el mismo texto). */
public record ReservationCodeRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.RESERVATION_CODE, message = "formato de código inválido")
        String code
) {
}
