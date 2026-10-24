package com.ucentral.desarrollos.backendparkspotter.auth.service;

import com.ucentral.desarrollos.backendparkspotter.auth.dto.TokenPairResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.ChangePasswordRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LoginRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LogoutRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RegisterRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RefreshRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.UserResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.AccountType;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.LoginAudit;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.RoleNames;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.LoginAuditRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RateLimitService;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RefreshTokenService;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ApiException;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.ConflictException;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    static final String INVALID_CREDENTIALS = "Credenciales inválidas";

    /**
     * Hash de una contraseña aleatoria. Se compara contra él cuando el correo no existe,
     * para que el tiempo de respuesta no revele qué correos están registrados.
     */
    private volatile String dummyHash;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RateLimitService rateLimitService;
    private final LoginAuditRepository loginAuditRepository;

    @Transactional
    public TokenPairResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("El correo ya está registrado");
        }
        AccountType accountType = request.role() == null ? AccountType.DRIVER : request.role();
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(cleanOptional(request.fullName()));
        user.setPhone(cleanOptional(request.phone()));
        user.getRoles().add(Roles.findOrCreate(roleRepository, RoleNames.USER));
        user.getRoles().add(Roles.findOrCreate(roleRepository, accountType.roleName()));
        UserAccount saved = userRepository.save(user);
        audit(email, "REGISTER", "unknown", "unknown", "unknown");
        return tokenPair(saved, refreshTokenService.createRefreshToken(saved));
    }

    @Transactional
    public TokenPairResponse login(LoginRequest request, String clientKey) {
        String email = normalizeEmail(request.email());
        validateLoginHeaders(clientKey);
        rateLimitService.assertAllowed(clientKey + ":" + email);
        UserAccount user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches(request.password(), dummyHash());
            rateLimitService.recordFailure(clientKey + ":" + email);
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        // Un usuario deshabilitado recibe el mismo mensaje genérico: no se revela el estado de la cuenta.
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()) || !user.isEnabled()) {
            rateLimitService.recordFailure(clientKey + ":" + email);
            audit(email, "LOGIN_FAILED", clientKey, "unknown", "unknown");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        rateLimitService.recordSuccess(clientKey + ":" + email);
        audit(email, "LOGIN_SUCCESS", clientKey, "unknown", "unknown");
        return tokenPair(user, refreshTokenService.createRefreshToken(user));
    }

    @Transactional(readOnly = true)
    public UserResponse me(UserAccount user) {
        return toUserResponse(user);
    }

    @Transactional
    public TokenPairResponse refresh(RefreshRequest request) {
        RefreshTokenService service = refreshTokenService;
        String rawToken = request.refreshToken();
        var token = service.findValidToken(rawToken)
                .orElseThrow(() -> new UnauthorizedException("Refresh token inválido o vencido"));
        UserAccount user = token.getUser();
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Refresh token inválido o vencido");
        }
        return tokenPair(user, service.rotate(rawToken, user));
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UserAccount user) {
        // 400 y no 401: el usuario sí está autenticado, solo se equivocó de contraseña actual.
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiException("La contraseña actual no es correcta");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /** Respuesta de login/registro/refresh: access token con los roles actuales y el refresh token. */
    public TokenPairResponse tokenPair(UserAccount user, String refreshToken) {
        List<String> roles = user.getRoles().stream().map(Role::getName).sorted().toList();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        return new TokenPairResponse(user.getId(), user.getEmail(), accessToken, refreshToken, "Bearer",
                jwtService.accessTokenSeconds(), roles);
    }

    public static UserResponse toUserResponse(UserAccount user) {
        Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toCollection(TreeSet::new));
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(), roles,
                user.isEnabled(), user.getCreatedAt());
    }

    private static String cleanOptional(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
        }
        return dummyHash;
    }

    private void validateLoginHeaders(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new ApiException("Falta el identificador de cliente (X-Client-Id)");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void audit(String email, String outcome, String clientId, String ipAddress, String userAgent) {
        var audit = new LoginAudit();
        audit.setEmail(email);
        audit.setOutcome(outcome);
        audit.setClientId(clientId);
        audit.setIpAddress(ipAddress);
        audit.setUserAgent(userAgent);
        loginAuditRepository.save(audit);
    }
}
