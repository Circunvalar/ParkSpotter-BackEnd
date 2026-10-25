package com.ucentral.desarrollos.backendparkspotter.user.dto;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.AccountType;
import jakarta.validation.constraints.NotNull;

/** Activa el otro perfil en la misma cuenta (un conductor que también quiere publicar su garaje, o al revés). */
public record AddRoleRequest(@NotNull AccountType role) {
}
