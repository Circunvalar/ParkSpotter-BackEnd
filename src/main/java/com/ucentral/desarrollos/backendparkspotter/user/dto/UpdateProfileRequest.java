package com.ucentral.desarrollos.backendparkspotter.user.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationPatterns;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Edición del perfil: los campos null no cambian; un texto vacío borra el dato.
 * El correo no se edita aquí (es el usuario de inicio de sesión).
 */
public record UpdateProfileRequest(
        @Size(max = 120)
        @Pattern(regexp = "^$|" + ValidationPatterns.PERSON_NAME, message = "solo puede contener letras, espacios, puntos, apóstrofes y guiones")
        String fullName,
        @Pattern(regexp = "^$|" + ValidationPatterns.PHONE, message = "debe ser un teléfono válido (7 a 20 dígitos, puede iniciar con +)")
        String phone
) {
}
