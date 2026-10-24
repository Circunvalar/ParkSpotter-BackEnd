package com.ucentral.desarrollos.backendparkspotter.auth.service;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.PasswordResetToken;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.PasswordResetTokenRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RateLimitService;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RefreshTokenService;
import com.ucentral.desarrollos.backendparkspotter.notification.service.EmailSender;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import com.ucentral.desarrollos.backendparkspotter.shared.security.SecureTokens;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Recuperación de contraseña por correo (Sprint 06, PB-07).
 *
 * forgot nunca revela si el correo existe: siempre responde igual. El token es de un solo uso,
 * vence en app.security.password-reset-ttl y al usarlo se cierran todas las sesiones abiertas.
 */
@Slf4j
@Service
public class PasswordResetService {

    static final String INVALID_TOKEN = "El enlace de recuperación no es válido o ya venció. Solicita uno nuevo.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RateLimitService rateLimitService;
    private final EmailSender emailSender;
    private final Clock clock;
    private final Duration ttl;
    private final String resetUrl;

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetTokenRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                RefreshTokenService refreshTokenService,
                                RateLimitService rateLimitService,
                                EmailSender emailSender,
                                Clock clock,
                                @Value("${app.security.password-reset-ttl:PT30M}") Duration ttl,
                                @Value("${app.frontend.reset-password-url:http://localhost:3000/restablecer-contrasena}") String resetUrl) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.rateLimitService = rateLimitService;
        this.emailSender = emailSender;
        this.clock = clock;
        this.ttl = ttl;
        this.resetUrl = resetUrl;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        // Se cuentan todas las solicitudes (exista o no el correo) para frenar el abuso sin revelar nada.
        String rateKey = "password-reset:" + email;
        rateLimitService.assertAllowed(rateKey);
        rateLimitService.recordFailure(rateKey);

        UserAccount user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null || !user.isEnabled() || user.getDeletedAt() != null) {
            return;
        }
        Instant now = Instant.now(clock);
        tokenRepository.invalidateAllForUser(user.getId(), now);
        String rawToken = SecureTokens.randomToken(32);
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(SecureTokens.sha256Base64(rawToken));
        token.setExpiresAt(now.plus(ttl));
        tokenRepository.save(token);

        String link = resetUrl + (resetUrl.contains("?") ? "&" : "?") + "token=" + rawToken;
        String body = """
                Hola,

                Recibimos una solicitud para restablecer la contraseña de tu cuenta de ParkSpotter.
                Abre este enlace (vence en %d minutos):

                %s

                Si estás en la app, también puedes pegar este código: %s

                Si no fuiste tú, ignora este correo: tu contraseña no cambia.
                """.formatted(ttl.toMinutes(), link, rawToken);
        String to = user.getEmail();
        runAfterCommit(() -> emailSender.send(to, "Restablece tu contraseña de ParkSpotter", body));
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordResetToken token = tokenRepository.findByTokenHash(SecureTokens.sha256Base64(rawToken))
                .filter(t -> t.getUsedAt() == null && t.getExpiresAt().isAfter(now))
                .orElseThrow(() -> new ApiException(INVALID_TOKEN));
        UserAccount user = token.getUser();
        if (!user.isEnabled() || user.getDeletedAt() != null) {
            throw new ApiException(INVALID_TOKEN);
        }
        token.setUsedAt(now);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
        rateLimitService.recordSuccess("password-reset:" + user.getEmail());
    }

    private static void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
