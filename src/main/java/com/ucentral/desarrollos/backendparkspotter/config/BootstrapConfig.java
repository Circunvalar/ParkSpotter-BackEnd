package com.ucentral.desarrollos.backendparkspotter.config;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
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
            roleRepository.findByName("ROLE_USER").orElseGet(() -> {
                Role role = new Role();
                role.setName("ROLE_USER");
                return roleRepository.save(role);
            });
        };
    }
}
