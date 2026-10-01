package com.ucentral.desarrollos.backendparkspotter.garageManagement.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.Garage;
import org.springframework.security.access.AccessDeniedException;

/**
 * Solo el dueño del garaje o un administrador pueden gestionarlo (datos, estado y plazas).
 */
public final class GarageAccessPolicy {

    private GarageAccessPolicy() {
    }

    public static void assertCanManage(Garage garage, UserAccount user) {
        boolean isOwner = user != null && garage.getOwner().getId().equals(user.getId());
        boolean isAdmin = user != null && user.getRoles().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getName()));
        if (!isOwner && !isAdmin) {
            throw new AccessDeniedException("No tienes permisos sobre este garaje");
        }
    }
}
