package com.ucentral.desarrollos.backendparkspotter.auth;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.RefreshToken;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RefreshTokenRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Refresh tokens opacos: se guardan solo como hash, vencen y se revocan.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock RefreshTokenRepository repository;

    RefreshTokenService service;
    UserAccount user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService("P7D", repository);
        user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail("token@parkspotter.co");
    }

    @Test
    void create_StoresOnlyTheHash_WithExpiration() {
        String raw = service.createRefreshToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertThat(raw).matches("^[A-Za-z0-9_-]{80,}$");
        assertThat(stored.getTokenHash()).isNotEqualTo(raw).hasSize(43);
        assertThat(stored.getUser()).isEqualTo(user);
        assertThat(stored.getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(Duration.ofMinutes(1)));
    }

    @Test
    void create_GeneratesDifferentTokensEachTime() {
        assertThat(service.createRefreshToken(user)).isNotEqualTo(service.createRefreshToken(user));
    }

    @Test
    void ttl_AcceptsIsoDurationWithOrWithoutPrefix() {
        RefreshTokenService shortForm = new RefreshTokenService("7d", repository);

        shortForm.createRefreshToken(user);

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(Duration.ofMinutes(1)));
    }

    @Test
    void findValidToken_ReturnsOnlyActiveAndNotExpiredTokens() {
        RefreshToken active = token(false, Instant.now().plusSeconds(60));
        RefreshToken revoked = token(true, Instant.now().plusSeconds(60));
        RefreshToken expired = token(false, Instant.now().minusSeconds(1));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(active), Optional.of(revoked), Optional.of(expired), Optional.empty());

        assertThat(service.findValidToken("a")).contains(active);
        assertThat(service.findValidToken("b")).isEmpty();
        assertThat(service.findValidToken("c")).isEmpty();
        assertThat(service.findValidToken("d")).isEmpty();
    }

    @Test
    void sameRawToken_AlwaysProducesTheSameHash() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());
        ArgumentCaptor<String> hashes = ArgumentCaptor.forClass(String.class);

        service.findValidToken("mismo-token");
        service.findValidToken("mismo-token");

        verify(repository, org.mockito.Mockito.times(2)).findByTokenHash(hashes.capture());
        assertThat(hashes.getAllValues().get(0)).isEqualTo(hashes.getAllValues().get(1));
    }

    @Test
    void revoke_MarksTheTokenAsRevoked() {
        RefreshToken stored = token(false, Instant.now().plusSeconds(60));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));

        service.revoke("raw");

        assertThat(stored.isRevoked()).isTrue();
        verify(repository).save(stored);
    }

    @Test
    void revoke_OfUnknownToken_DoesNothing() {
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        service.revoke("desconocido");

        verify(repository, never()).save(any());
    }

    @Test
    void rotate_RevokesTheOldTokenAndIssuesANewOne() {
        RefreshToken old = token(false, Instant.now().plusSeconds(60));
        when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(old));

        String rotated = service.rotate("viejo", user);

        assertThat(old.isRevoked()).isTrue();
        assertThat(rotated).isNotBlank().isNotEqualTo("viejo");
    }

    private RefreshToken token(boolean revoked, Instant expiresAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setRevoked(revoked);
        token.setExpiresAt(expiresAt);
        return token;
    }
}
