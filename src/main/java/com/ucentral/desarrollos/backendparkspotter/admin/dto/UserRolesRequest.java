package com.ucentral.desarrollos.backendparkspotter.admin.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** @param roles roles finales de la cuenta: DRIVER, OWNER y/o ADMIN (ROLE_USER se conserva siempre) */
public record UserRolesRequest(@NotEmpty @Size(max = 3) Set<ManagedRole> roles) {

    public enum ManagedRole { DRIVER, OWNER, ADMIN }
}
