package com.ucentral.desarrollos.backendparkspotter.auth;

import com.ucentral.desarrollos.backendparkspotter.auth.dto.ChangePasswordRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LoginRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LogoutRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RefreshRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RegisterRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.TokenPairResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.UserResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.LoginAudit;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.RefreshToken;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.Role;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.LoginAuditRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.RoleRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.repository.UserRepository;
import com.ucentral.desarrollos.backendparkspotter.auth.security.JwtService;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RateLimitService;
import com.ucentral.desarrollos.backendparkspotter.auth.security.RefreshTokenService;
import com.ucentral.desarrollos.backendparkspotter.auth.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias puras de AuthService: todas las dependencias van mockeadas
 * (sin Spring, sin base de datos). Cada método se puede correr individualmente con @Test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceUnitTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock RateLimitService rateLimitService;
    @Mock LoginAuditRepository loginAuditRepository;

    @InjectMocks
    AuthService authService;

    @BeforeEach
    void defaultStubs() {
        when(userRepository.save(any(UserAccount.class))).thenAnswer(inv -> {
            UserAccount u = inv.getArgument(0);
            if (u.getId() == null) u.setId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateAccessToken(any(), anyString(), any())).thenReturn("access-token");
        when(jwtService.accessTokenSeconds()).thenReturn(900L);
        when(refreshTokenService.createRefreshToken(any(UserAccount.class))).thenReturn("refresh-token");
    }

    // ---------- register ----------

    @Test
    void register_WhenEmailNotTaken_ShouldCreateUserAndReturnTokenPair() {
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role("ROLE_USER")));
        when(passwordEncoder.encode("Password123456")).thenReturn("hashed-pw");

        RegisterRequest request = new RegisterRequest("  USER@Example.com  ", "Password123456");
        TokenPairResponse response = authService.register(request);

        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        verify(roleRepository, never()).save(any());
        verify(loginAuditRepository, times(1)).save(any(LoginAudit.class));
    }

    @Test
    void register_WhenEmailAlreadyRegistered_ShouldThrow_AndNotCreateUser() {
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);
        RegisterRequest request = new RegisterRequest("dup@example.com", "Password123456");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
        verify(loginAuditRepository, never()).save(any());
    }

    @Test
    void register_WhenDefaultRoleMissing_ShouldCreateItExactlyOnce() {
        when(userRepository.existsByEmailIgnoreCase("new@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pw");

        authService.register(new RegisterRequest("new@example.com", "Password123456"));

        verify(roleRepository, times(1)).save(any(Role.class));
    }

    // ---------- login ----------

    @Test
    void login_WhenCredentialsValid_ShouldReturnTokensAndRecordSuccess() {
        UserAccount user = user("login@example.com", "hashed-pw");
        when(userRepository.findByEmailIgnoreCase("login@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("rawPassword", "hashed-pw")).thenReturn(true);

        TokenPairResponse response = authService.login(new LoginRequest("login@example.com", "rawPassword"), "client-1");

        assertThat(response.accessToken()).isEqualTo("access-token");
        verify(rateLimitService).assertAllowed("client-1:login@example.com");
        verify(rateLimitService).recordSuccess("client-1:login@example.com");
        verify(rateLimitService, never()).recordFailure(anyString());

        ArgumentCaptor<LoginAudit> auditCaptor = ArgumentCaptor.forClass(LoginAudit.class);
        verify(loginAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getOutcome()).isEqualTo("LOGIN_SUCCESS");
    }

    @Test
    void login_WhenClientKeyBlank_ShouldThrow_BeforeTouchingRepository() {
        assertThatThrownBy(() -> authService.login(new LoginRequest("any@example.com", "pw"), " "))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userRepository);
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void login_WhenUserNotFound_ShouldThrowAndRecordFailure() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost@example.com", "pw"), "client-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");

        verify(rateLimitService).recordFailure("client-2:ghost@example.com");
    }

    @Test
    void login_WhenPasswordDoesNotMatch_ShouldThrowRecordFailureAndAudit() {
        UserAccount user = user("wrongpw@example.com", "hashed-pw");
        when(userRepository.findByEmailIgnoreCase("wrongpw@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("badPassword", "hashed-pw")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("wrongpw@example.com", "badPassword"), "client-3"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(rateLimitService).recordFailure("client-3:wrongpw@example.com");
        ArgumentCaptor<LoginAudit> auditCaptor = ArgumentCaptor.forClass(LoginAudit.class);
        verify(loginAuditRepository).save(auditCaptor.capture());
        assertThat(auditCaptor.getValue().getOutcome()).isEqualTo("LOGIN_FAILED");
    }

    @Test
    void login_WhenRateLimited_ShouldThrow_BeforeCheckingCredentials() {
        doThrow(new IllegalStateException("Too many login attempts. Try again later."))
                .when(rateLimitService).assertAllowed(anyString());

        assertThatThrownBy(() -> authService.login(new LoginRequest("locked@example.com", "pw"), "client-4"))
                .isInstanceOf(IllegalStateException.class);

        verifyNoInteractions(userRepository);
    }

    // ---------- me ----------

    @Test
    void me_ShouldMapRolesAndEnabledFlag() {
        UserAccount user = user("me@example.com", "hashed-pw");
        user.getRoles().add(role("ROLE_USER"));
        user.getRoles().add(role("ROLE_ADMIN"));
        user.setEnabled(true);

        UserResponse response = authService.me(user);

        assertThat(response.id()).isEqualTo(user.getId());
        assertThat(response.email()).isEqualTo("me@example.com");
        assertThat(response.roles()).isEqualTo(Set.of("ROLE_USER", "ROLE_ADMIN"));
        assertThat(response.enabled()).isTrue();
    }

    // ---------- refresh ----------

    @Test
    void refresh_WhenTokenValid_ShouldReturnNewTokenPair() {
        UserAccount user = user("refresh@example.com", "hashed-pw");
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        when(refreshTokenService.findValidToken("raw-token")).thenReturn(Optional.of(token));
        when(refreshTokenService.rotate(eq("raw-token"), eq(user))).thenReturn("rotated-refresh-token");
        when(jwtService.generateAccessToken(user.getId(), user.getEmail(), List.of()))
                .thenReturn("new-access-token");

        TokenPairResponse response = authService.refresh(new RefreshRequest("raw-token"));

        assertThat(response.refreshToken()).isEqualTo("rotated-refresh-token");
        assertThat(response.userId()).isEqualTo(user.getId());
    }

    @Test
    void refresh_WhenTokenInvalid_ShouldThrow() {
        when(refreshTokenService.findValidToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("bad-token")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- changePassword ----------

    @Test
    void changePassword_WhenCurrentPasswordMatches_ShouldUpdateHashAndSave() {
        UserAccount user = user("pwchange@example.com", "old-hash");
        when(passwordEncoder.matches("oldPass", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword1234")).thenReturn("new-hash");

        authService.changePassword(new ChangePasswordRequest("oldPass", "newPassword1234"), user);

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_WhenCurrentPasswordInvalid_ShouldThrow_AndNotSave() {
        UserAccount user = user("pwfail@example.com", "old-hash");
        when(passwordEncoder.matches("wrongOldPass", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(
                new ChangePasswordRequest("wrongOldPass", "newPassword1234"), user))
                .isInstanceOf(IllegalArgumentException.class);

        verify(userRepository, never()).save(any());
    }

    // ---------- logout ----------

    @Test
    void logout_ShouldDelegateRevokeToRefreshTokenService() {
        authService.logout(new LogoutRequest("token-to-revoke"));

        verify(refreshTokenService, times(1)).revoke("token-to-revoke");
    }

    // ---------- helpers ----------

    private UserAccount user(String email, String passwordHash) {
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setEnabled(true);
        return user;
    }

    private Role role(String name) {
        Role role = new Role();
        role.setId(UUID.randomUUID());
        role.setName(name);
        return role;
    }
}
