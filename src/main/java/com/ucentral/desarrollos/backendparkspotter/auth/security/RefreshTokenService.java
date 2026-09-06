package com.ucentral.desarrollos.backendparkspotter.auth.security;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.RefreshToken;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration refreshTokenTtl;

    public RefreshTokenService(@Value("${app.jwt.refresh-token-ttl}") String refreshTokenTtl,
                               RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        String normalized = refreshTokenTtl.trim().toUpperCase();
        this.refreshTokenTtl = normalized.startsWith("P") ? Duration.parse(normalized) : Duration.parse("P" + normalized);
    }

    @Transactional
    public String createRefreshToken(UserAccount user) {
        String rawToken = generateRawToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(refreshTokenTtl));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    @Transactional
    public String rotate(String rawToken, UserAccount user) {
        revoke(rawToken);
        return createRefreshToken(user);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public Optional<RefreshToken> findValidToken(String rawToken) {
        String tokenHash = hash(rawToken);
        return refreshTokenRepository.findByTokenHash(tokenHash)
                .filter(token -> !token.isRevoked() && token.getExpiresAt().isAfter(Instant.now()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
