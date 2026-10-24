package com.ucentral.desarrollos.backendparkspotter.notification.dto;

import com.ucentral.desarrollos.backendparkspotter.notification.entity.DevicePlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param token token de registro de Firebase Cloud Messaging del dispositivo
 */
public record DeviceTokenRequest(
        @NotBlank @Size(max = 512)
        @Pattern(regexp = "^[A-Za-z0-9:_\\-.]+$", message = "formato de token inválido")
        String token,
        @NotNull DevicePlatform platform
) {
}
