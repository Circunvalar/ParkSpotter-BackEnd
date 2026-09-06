package com.ucentral.desarrollos.backendparkspotter.auth.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.dto.ChangePasswordRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LoginRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LogoutRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RefreshRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RegisterRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.UserResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.TokenPairResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<TokenPairResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenPairResponse> login(@Valid @RequestBody LoginRequest request,
                                              @RequestHeader(value = "X-Client-Id", defaultValue = "browser") String clientId) {
        return ResponseEntity.ok(authService.login(request, clientId));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(authService.me(user));
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               @AuthenticationPrincipal UserAccount user) {
        authService.changePassword(request, user);
        return ResponseEntity.noContent().build();
    }
}
