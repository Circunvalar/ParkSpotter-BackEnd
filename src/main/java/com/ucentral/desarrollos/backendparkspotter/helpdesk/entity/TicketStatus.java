package com.ucentral.desarrollos.backendparkspotter.helpdesk.entity;

/** OPEN: esperando a soporte. IN_PROGRESS: soporte respondió. RESOLVED: solucionado (el usuario puede reabrir respondiendo). CLOSED: cerrado definitivamente. */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED
}
