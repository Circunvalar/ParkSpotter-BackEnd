package com.ucentral.desarrollos.backendparkspotter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Se pide la contraseña actual para confirmar una acción que no se puede deshacer. */
public record DeleteAccountRequest(@NotBlank @Size(max = 72) String password) {
}
