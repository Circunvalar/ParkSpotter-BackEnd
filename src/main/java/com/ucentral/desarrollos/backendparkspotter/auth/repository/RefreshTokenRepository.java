package com.ucentral.desarrollos.backendparkspotter.auth.repository;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    long countByUserIdAndRevokedFalseAndExpiresAtAfter(UUID userId, Instant now);

    /** Cierra todas las sesiones del usuario (cambio o recuperación de contraseña, cuenta eliminada o deshabilitada). */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true WHERE t.user.id = :userId AND t.revoked = false")
    int revokeAllForUser(@Param("userId") UUID userId);
}
