package com.ucentral.desarrollos.backendparkspotter.config;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.RoleNames;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.repository.GarageRepository;
import com.ucentral.desarrollos.backendparkspotter.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Cuentas creadas antes del Sprint 04 (solo ROLE_USER) reciben rol de conductor o propietario. */
class AccountRoleMigrationTest extends ApiTestSupport {

    @Autowired GarageRepository garageRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @Test
    void run_AssignsOwnerOrDriver_AndAdminByEmail_Idempotently() {
        UserAccount legacyOwner = user("legado-dueno@parkspotter.co");
        UserAccount legacyDriver = user("legado-conductor@parkspotter.co");
        garage24h(legacyOwner, "Garaje Legado", 1);
        AccountRoleMigration migration = new AccountRoleMigration(userRepository, roleRepository, garageRepository, " legado-conductor@parkspotter.co ");

        assertThat(transactionTemplate.<Integer>execute(status -> migration.run()).intValue()).isEqualTo(2);
        assertThat(roles(legacyOwner)).containsExactlyInAnyOrder(RoleNames.USER, RoleNames.OWNER);
        assertThat(roles(legacyDriver)).containsExactlyInAnyOrder(RoleNames.USER, RoleNames.DRIVER, RoleNames.ADMIN);

        assertThat(transactionTemplate.<Integer>execute(status -> migration.run()).intValue()).isZero();
        AccountRoleMigration withoutAdmin = new AccountRoleMigration(userRepository, roleRepository, garageRepository, "");
        assertThat(transactionTemplate.<Integer>execute(status -> withoutAdmin.run()).intValue()).isZero();
    }

    private Set<String> roles(UserAccount user) {
        return transactionTemplate.execute(status -> userRepository.findById(user.getId()).orElseThrow()
                .getRoles().stream().map(Role::getName).collect(Collectors.toSet()));
    }
}
