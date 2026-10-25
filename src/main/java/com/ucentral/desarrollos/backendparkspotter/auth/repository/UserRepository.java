package com.ucentral.desarrollos.backendparkspotter.auth.repository;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserAccount, UUID>, JpaSpecificationExecutor<UserAccount> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Query("SELECT COUNT(DISTINCT u) FROM UserAccount u JOIN u.roles r WHERE u.deletedAt IS NULL AND r.name = :role")
    long countActiveByRole(@Param("role") String role);

    long countByDeletedAtIsNull();

    long countByDeletedAtIsNullAndEnabledFalse();

    long countByDeletedAtIsNullAndCreatedAtBetween(Instant from, Instant to);

    /** Cuentas creadas antes del Sprint 04 que aún no tienen rol de conductor ni de propietario. */
    @Query("""
            SELECT u FROM UserAccount u
            WHERE NOT EXISTS (SELECT 1 FROM UserAccount u2 JOIN u2.roles r
                              WHERE u2 = u AND r.name IN ('ROLE_DRIVER', 'ROLE_OWNER', 'ROLE_ADMIN'))
            """)
    List<UserAccount> findWithoutAccountRole();
}
