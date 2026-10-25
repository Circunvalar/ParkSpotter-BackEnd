package com.ucentral.desarrollos.backendparkspotter.auth.entity;

/**
 * Tipo de cuenta que se elige en el registro. Una cuenta puede activar después el otro rol
 * (POST /api/v1/users/me/roles).
 */
public enum AccountType {
    DRIVER(RoleNames.DRIVER),
    OWNER(RoleNames.OWNER);

    private final String roleName;

    AccountType(String roleName) {
        this.roleName = roleName;
    }

    public String roleName() {
        return roleName;
    }
}
