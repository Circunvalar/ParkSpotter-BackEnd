package com.ucentral.desarrollos.backendparkspotter.config;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.ParkingSpotService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Beans de configuración: roles iniciales, reloj, generación de plazas y seguridad.
 */
class ConfigTest {

    @Test
    void initRoles_CreatesRoleUser_WhenItDoesNotExist() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.empty());
        when(roles.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        new BootstrapConfig().initRoles(roles).run();

        verify(roles).save(any(Role.class));
    }

    @Test
    void initRoles_DoesNothing_WhenRoleUserAlreadyExists() throws Exception {
        RoleRepository roles = mock(RoleRepository.class);
        when(roles.findByName("ROLE_USER")).thenReturn(Optional.of(new Role()));

        new BootstrapConfig().initRoles(roles).run();

        verify(roles, never()).save(any());
    }

    @Test
    void clock_UsesConfiguredTimeZone() {
        Clock clock = new GarageConfig().clock(ZoneId.of("America/Bogota"));

        assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Bogota"));
    }

    @Test
    void backfillRunner_DelegatesToParkingSpotService() throws Exception {
        ParkingSpotService service = mock(ParkingSpotService.class);

        new GarageConfig().backfillParkingSpots(service).run(new DefaultApplicationArguments());

        verify(service).backfillLegacyGarages();
    }

    @Test
    void userDetailsService_NeverAuthenticatesByUsernameAndPassword() {
        SecurityConfig config = new SecurityConfig(null, null);

        assertThatThrownBy(() -> config.userDetailsService().loadUserByUsername("user"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void passwordEncoder_IsBcryptWithCost12() {
        PasswordEncoder encoder = new SecurityConfig(null, null).passwordEncoder();

        String hash = encoder.encode("Password123!");

        assertThat(hash).startsWith("$2a$12$");
        assertThat(encoder.matches("Password123!", hash)).isTrue();
    }
}
