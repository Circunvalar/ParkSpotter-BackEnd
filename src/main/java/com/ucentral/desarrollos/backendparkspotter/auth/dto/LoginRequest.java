package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * En el login no se aplica la política de contraseñas (solo se compara), pero sí el límite
 * de 72 caracteres de BCrypt para no procesar entradas gigantes.
 */
public record LoginRequest(
        @NotBlank @Size(max = 254)
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "debe ser un correo válido, ej. nombre@dominio.com")
        String email,
        @NotBlank @Size(max = 72) String password
) {
}
