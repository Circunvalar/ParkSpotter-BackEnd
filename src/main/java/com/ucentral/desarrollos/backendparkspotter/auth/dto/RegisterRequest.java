package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidPassword;
import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 254)
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "debe ser un correo válido, ej. nombre@dominio.com")
        String email,

        @NotBlank @ValidPassword
        String password
) {
}
