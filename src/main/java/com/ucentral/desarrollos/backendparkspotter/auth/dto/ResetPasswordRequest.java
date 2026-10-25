package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidPassword;
import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** @param token el token que llegó por correo (en el enlace o copiado a mano en la app) */
public record ResetPasswordRequest(
        @NotBlank @Size(max = 200)
        @Pattern(regexp = ValidationPatterns.OPAQUE_TOKEN, message = "formato de token inválido")
        String token,
        @NotBlank @ValidPassword String newPassword
) {
}
