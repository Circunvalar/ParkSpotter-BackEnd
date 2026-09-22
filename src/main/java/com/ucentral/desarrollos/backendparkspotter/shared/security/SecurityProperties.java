package com.ucentral.desarrollos.backendparkspotter.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        int maxLoginAttempts,
        Duration lockoutDuration,
        Duration loginWindow,
        List<String> trustedDeviceHeaders,
        boolean requireTrustedDeviceHeader
) {
}
