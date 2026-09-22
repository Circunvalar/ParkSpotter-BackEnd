package com.ucentral.desarrollos.backendparkspotter.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitService {

    private final int maxAttempts;
    private final Duration lockoutDuration;
    private final Duration loginWindow;
    private final Map<String, AttemptWindow> windows = new ConcurrentHashMap<>();

    public RateLimitService(
            @Value("${app.security.max-login-attempts}") int maxAttempts,
            @Value("${app.security.lockout-duration}") Duration lockoutDuration,
            @Value("${app.security.login-window}") Duration loginWindow
    ) {
        this.maxAttempts = maxAttempts;
        this.lockoutDuration = lockoutDuration;
        this.loginWindow = loginWindow;
    }

    public void assertAllowed(String key) {
        AttemptWindow window = windows.get(key);
        if (window != null && window.lockedUntil != null && window.lockedUntil.isAfter(Instant.now())) {
            throw new IllegalStateException("Too many login attempts. Try again later.");
        }
    }

    public void recordFailure(String key) {
        windows.compute(key, (k, window) -> {
            AttemptWindow current = window == null ? new AttemptWindow() : window;
            if (current.resetAt == null || current.resetAt.isBefore(Instant.now())) {
                current.count.set(0);
                current.resetAt = Instant.now().plus(loginWindow);
                current.lockedUntil = null;
            }
            int attempts = current.count.incrementAndGet();
            if (attempts >= maxAttempts) {
                current.lockedUntil = Instant.now().plus(lockoutDuration);
                current.count.set(0);
            }
            return current;
        });
    }

    public void recordSuccess(String key) {
        windows.remove(key);
    }

    public boolean isLocked(String key) {
        AttemptWindow window = windows.get(key);
        return window != null && window.lockedUntil != null && window.lockedUntil.isAfter(Instant.now());
    }

    private static final class AttemptWindow {
        private final AtomicInteger count = new AtomicInteger();
        private Instant resetAt;
        private Instant lockedUntil;
    }
}
