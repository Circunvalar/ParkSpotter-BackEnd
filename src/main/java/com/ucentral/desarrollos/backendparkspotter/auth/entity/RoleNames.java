package com.ucentral.desarrollos.backendparkspotter.auth.entity;

/**
 * Nombres de los roles del sistema (Sprint 04, PB-06).
 * ROLE_USER lo tienen todas las cuentas; ROLE_DRIVER y ROLE_OWNER definen qué puede hacer cada una.
 */
public final class RoleNames {

    public static final String USER = "ROLE_USER";
    /** Conductor: busca, reserva, paga y califica. */
    public static final String DRIVER = "ROLE_DRIVER";
    /** Propietario: publica garajes, registra entradas y salidas y ve sus reportes. */
    public static final String OWNER = "ROLE_OWNER";
    public static final String ADMIN = "ROLE_ADMIN";

    private RoleNames() {
    }
}
