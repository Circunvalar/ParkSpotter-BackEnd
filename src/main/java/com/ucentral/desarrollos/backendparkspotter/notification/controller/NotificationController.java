package com.ucentral.desarrollos.backendparkspotter.notification.controller;

import com.ucentral.desarrollos.backendparkspotter.auth.entity.UserAccount;
import com.ucentral.desarrollos.backendparkspotter.notification.dto.DeviceTokenRequest;
import com.ucentral.desarrollos.backendparkspotter.notification.dto.NotificationResponse;
import com.ucentral.desarrollos.backendparkspotter.notification.dto.UnreadCountResponse;
import com.ucentral.desarrollos.backendparkspotter.notification.service.NotificationService;
import com.ucentral.desarrollos.backendparkspotter.shared.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Bandeja de notificaciones del usuario y registro de dispositivos para push. */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationResponse>> list(@AuthenticationPrincipal UserAccount user,
                                                                   @RequestParam(defaultValue = "false") boolean unreadOnly,
                                                                   @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
                                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(notificationService.list(user, unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(@AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(notificationService.unreadCount(user));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id, @AuthenticationPrincipal UserAccount user) {
        return ResponseEntity.ok(notificationService.markRead(id, user));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal UserAccount user) {
        notificationService.markAllRead(user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/devices")
    public ResponseEntity<Void> registerDevice(@Valid @RequestBody DeviceTokenRequest request,
                                               @AuthenticationPrincipal UserAccount user) {
        notificationService.registerDevice(request, user);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices")
    public ResponseEntity<Void> unregisterDevice(@RequestParam @NotBlank @Size(max = 512) String token,
                                                 @AuthenticationPrincipal UserAccount user) {
        notificationService.unregisterDevice(token, user);
        return ResponseEntity.noContent().build();
    }
}
