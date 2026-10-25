package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.AccountType;
import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidPassword;
import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param role     DRIVER (por defecto) u OWNER
 * @param fullName opcional en el registro; se puede completar después en el perfil
 */
public record RegisterRequest(
        @NotBlank @Size(max = 254)
        @Pattern(regexp = ValidationPatterns.EMAIL, message = "debe ser un correo válido, ej. nombre@dominio.com")
        String email,

        @NotBlank @ValidPassword
        String password,

        AccountType role,

        @Size(max = 120)
        @Pattern(regexp = ValidationPatterns.PERSON_NAME, message = "solo puede contener letras, espacios, puntos, apóstrofes y guiones")
        String fullName,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "debe ser un teléfono válido (7 a 20 dígitos, puede iniciar con +)")
        String phone
) {

    /** Registro sin rol ni datos de perfil (compatibilidad con los fronts del Sprint 01): queda como conductor. */
    public RegisterRequest(String email, String password) {
        this(email, password, null, null, null);
    }
}
