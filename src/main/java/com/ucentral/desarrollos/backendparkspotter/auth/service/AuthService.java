package com.ucentral.desarrollos.backendparkspotter.auth.service;

import com.ucentral.desarrollos.backendparkspotter.auth.dto.TokenPairResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.ChangePasswordRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LoginRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LogoutRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RegisterRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RefreshRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.UserResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.LoginAuditRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RateLimitService;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

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
            throw new IllegalArgumentException("Email already registered");
        }
        Role role = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    Role created = new Role();
                    created.setName("ROLE_USER");
                    return roleRepository.save(created);
                });
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.getRoles().add(role);
        UserAccount saved = userRepository.save(user);
        audit(email, "REGISTER", "unknown", "unknown", "unknown");
        String accessToken = jwtService.generateAccessToken(saved.getId(), saved.getEmail(), saved.getRoles().stream().map(Role::getName).toList());
        String refreshToken = refreshTokenService.createRefreshToken(saved);
        return new TokenPairResponse(saved.getId(), saved.getEmail(), accessToken, refreshToken, "Bearer", jwtService.accessTokenSeconds());
    }

    @Transactional
    public TokenPairResponse login(LoginRequest request, String clientKey) {
        String email = normalizeEmail(request.email());
        validateLoginHeaders(clientKey);
        rateLimitService.assertAllowed(clientKey + ":" + email);
        UserAccount user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    rateLimitService.recordFailure(clientKey + ":" + email);
                    return new IllegalArgumentException("Invalid credentials");
                });
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimitService.recordFailure(clientKey + ":" + email);
            audit(email, "LOGIN_FAILED", clientKey, "unknown", "unknown");
            throw new IllegalArgumentException("Invalid credentials");
        }
        rateLimitService.recordSuccess(clientKey + ":" + email);
        audit(email, "LOGIN_SUCCESS", clientKey, "unknown", "unknown");
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRoles().stream().map(Role::getName).toList());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new TokenPairResponse(user.getId(), user.getEmail(), accessToken, refreshToken, "Bearer", jwtService.accessTokenSeconds());
    }

    @Transactional(readOnly = true)
    public UserResponse me(UserAccount user) {
        Set<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return new UserResponse(user.getId(), user.getEmail(), roles, user.isEnabled());
    }

    @Transactional
    public TokenPairResponse refresh(RefreshRequest request) {
        RefreshTokenService service = refreshTokenService;
        String rawToken = request.refreshToken();
        var token = service.findValidToken(rawToken)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        UserAccount user = token.getUser();
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRoles().stream().map(Role::getName).toList());
        String rotatedRefreshToken = service.rotate(rawToken, user);
        return new TokenPairResponse(user.getId(), user.getEmail(), accessToken, rotatedRefreshToken, "Bearer", jwtService.accessTokenSeconds());
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, UserAccount user) {
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private void validateLoginHeaders(String clientKey) {
        if (clientKey == null || clientKey.isBlank()) {
            throw new IllegalArgumentException("Missing client identifier");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void audit(String email, String outcome, String clientId, String ipAddress, String userAgent) {
        var audit = new com.ucentral.desarrollos.backendparkspotter.auth.entity.LoginAudit();
        audit.setEmail(email);
        audit.setOutcome(outcome);
        audit.setClientId(clientId);
        audit.setIpAddress(ipAddress);
        audit.setUserAgent(userAgent);
        loginAuditRepository.save(audit);
    }
}
