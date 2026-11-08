package com.ucentral.desarrollos.backendparkspotter.helpdesk.dto;

/** Canales de contacto de soporte, configurables con SUPPORT_EMAIL, SUPPORT_PHONE y SUPPORT_HOURS. */
public record ContactResponse(String email, String phone, String hours) {
}
