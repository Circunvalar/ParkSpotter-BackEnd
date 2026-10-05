package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LogoutRequest(
        @NotBlank @Size(max = 200)
        @Pattern(regexp = ValidationPatterns.OPAQUE_TOKEN, message = "formato de token inválido")
        String refreshToken
) {
}
