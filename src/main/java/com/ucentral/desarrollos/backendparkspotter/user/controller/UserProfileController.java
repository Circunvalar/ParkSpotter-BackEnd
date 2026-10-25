package com.ucentral.desarrollos.backendparkspotter.user.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.dto.UserResponse;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.user.dto.AddRoleRequest;
import com.ucentral.desarrollos.backendparkspotter.user.dto.DeleteAccountRequest;
import com.ucentral.desarrollos.backendparkspotter.user.dto.UpdateProfileRequest;
import com.ucentral.desarrollos.backendparkspotter.user.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil del usuario autenticado. */
@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    @GetMapping
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(profileService.me(user));
    }

    @PatchMapping
    public ResponseEntity<UserResponse> update(@Valid @RequestBody UpdateProfileRequest request,
                                               @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(profileService.update(request, user));
    }

    @PostMapping("/roles")
    public ResponseEntity<UserResponse> addRole(@Valid @RequestBody AddRoleRequest request,
                                                @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(profileService.addRole(request, user));
    }

    /** DELETE con cuerpo (la contraseña). Si el cliente HTTP no lo permite, usar POST /api/v1/users/me/delete. */
    @DeleteMapping
    public ResponseEntity<Void> delete(@Valid @RequestBody DeleteAccountRequest request,
                                       @AuthenticationPrincipal UserAccount user) {
        profileService.delete(request.password(), user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    public ResponseEntity<Void> deleteViaPost(@Valid @RequestBody DeleteAccountRequest request,
                                              @AuthenticationPrincipal UserAccount user) {
        return delete(request, user);
    }
}
