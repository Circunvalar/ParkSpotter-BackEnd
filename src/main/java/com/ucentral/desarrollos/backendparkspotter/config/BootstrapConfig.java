package com.ucentral.desarrollos.backendparkspotter.config;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.RoleNames;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.service.Roles;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BootstrapConfig {

    @Bean
    @ConditionalOnProperty(name = "app.bootstrap.roles", havingValue = "true", matchIfMissing = false)
    CommandLineRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            Roles.findOrCreate(roleRepository, RoleNames.USER);
            Roles.findOrCreate(roleRepository, RoleNames.DRIVER);
            Roles.findOrCreate(roleRepository, RoleNames.OWNER);
            Roles.findOrCreate(roleRepository, RoleNames.ADMIN);
        };
    }

    /**
     * Cuentas creadas antes del Sprint 04: quien ya publicó garajes queda como propietario y el resto
     * como conductor. También otorga ROLE_ADMIN al correo de app.bootstrap.admin-email (si existe la cuenta).
     */
    @Bean
    @ConditionalOnProperty(name = "app.bootstrap.legacy-roles", havingValue = "true", matchIfMissing = true)
    ApplicationRunner migrateLegacyRoles(AccountRoleMigration migration) {
        return args -> migration.run();
    }
}
