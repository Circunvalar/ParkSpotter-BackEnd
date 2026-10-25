package com.ucentral.desarrollos.backendparkspotter.admin.controller;

import com.ucentral.desarrollos.backendparkspotter.admin.dto.AdminUserResponse;
import com.ucentral.desarrollos.backendparkspotter.admin.dto.UserRolesRequest;
import com.ucentral.desarrollos.backendparkspotter.admin.dto.UserStatusRequest;
import com.ucentral.desarrollos.backendparkspotter.admin.service.AdminUserService;
import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.GarageResponse;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.dto.VerificationDecisionRequest;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.entity.VerificationStatus;
import com.ucentral.desarrollos.backendparkspotter.garageManagement.service.GarageVerificationService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Panel de administración: verificación de garajes (PB-26) y gestión de usuarios (PB-36). Solo ROLE_ADMIN. */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminManagementController {

    private final AdminUserService adminUserService;
    private final GarageVerificationService verificationService;

    // ---------- usuarios ----------

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserResponse>> users(@RequestParam(required = false) @Size(max = 100) String q,
                                                                 @RequestParam(required = false) @Size(max = 20) String role,
                                                                 @RequestParam(required = false) Boolean enabled,
                                                                 @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminUserService.search(q, role, enabled, page, size));
    }

    @PatchMapping("/users/{userId}/status")
    public ResponseEntity<AdminUserResponse> setStatus(@PathVariable UUID userId, @Valid @RequestBody UserStatusRequest request,
                                                       @AuthenticationPrincipal UserAccount admin) {
        return ResponseEntity.ok(adminUserService.setEnabled(userId, request.enabled(), admin));
    }

    @PutMapping("/users/{userId}/roles")
    public ResponseEntity<AdminUserResponse> setRoles(@PathVariable UUID userId, @Valid @RequestBody UserRolesRequest request,
                                                      @AuthenticationPrincipal UserAccount admin) {
        return ResponseEntity.ok(adminUserService.setRoles(userId, request, admin));
    }

    // ---------- verificación de garajes ----------

    @GetMapping("/garages")
    public ResponseEntity<PageResponse<GarageResponse>> garages(@RequestParam(defaultValue = "PENDING") VerificationStatus verificationStatus,
                                                                @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(verificationService.listByStatus(verificationStatus, page, size));
    }

    @PostMapping("/garages/{garageId}/verification")
    public ResponseEntity<GarageResponse> decide(@PathVariable UUID garageId, @Valid @RequestBody VerificationDecisionRequest request) {
        return ResponseEntity.ok(verificationService.decide(garageId, request));
    }
}
