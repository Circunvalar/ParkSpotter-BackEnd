package com.ucentral.desarrollos.backendparkspotter.auth.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;

/** Busca un rol por nombre y lo crea si todavía no existe (la tabla de roles puede venir vacía). */
public final class Roles {

    private Roles() {
    }

    public static Role findOrCreate(RoleRepository roleRepository, String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role created = new Role();
            created.setName(name);
            return roleRepository.save(created);
        });
    }
}
