package com.ucentral.desarrollos.backendparkspotter.auth.repository;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);
    long countByUserIdAndRevokedFalseAndExpiresAtAfter(UUID userId, Instant now);
}
