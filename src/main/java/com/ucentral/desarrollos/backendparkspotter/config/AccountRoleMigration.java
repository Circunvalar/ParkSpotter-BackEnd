package com.ucentral.desarrollos.backendparkspotter.config;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.RoleNames;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.service.Roles;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Migración idempotente de roles (ver BootstrapConfig#migrateLegacyRoles). */
@Slf4j
@Component
public class AccountRoleMigration {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final GarageRepository garageRepository;
    private final String adminEmail;

    public AccountRoleMigration(UserRepository userRepository, RoleRepository roleRepository, GarageRepository garageRepository,
                                @Value("${app.bootstrap.admin-email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.garageRepository = garageRepository;
        this.adminEmail = adminEmail;
    }

    @Transactional
    public int run() {
        List<UserAccount> legacy = userRepository.findWithoutAccountRole();
        if (!legacy.isEmpty()) {
            Role owner = Roles.findOrCreate(roleRepository, RoleNames.OWNER);
            Role driver = Roles.findOrCreate(roleRepository, RoleNames.DRIVER);
            for (UserAccount user : legacy) {
                boolean ownsGarages = !garageRepository.findByOwnerId(user.getId()).isEmpty();
                user.getRoles().add(ownsGarages ? owner : driver);
            }
            log.info("Se asignó rol de conductor o propietario a {} cuentas existentes", legacy.size());
        }
        if (!adminEmail.isBlank()) {
            userRepository.findByEmailIgnoreCase(adminEmail.trim()).ifPresent(user -> {
                if (!user.isAdmin()) {
                    user.getRoles().add(Roles.findOrCreate(roleRepository, RoleNames.ADMIN));
                    log.info("Se otorgó ROLE_ADMIN a la cuenta configurada en app.bootstrap.admin-email");
                }
            });
        }
        return legacy.size();
    }
}
