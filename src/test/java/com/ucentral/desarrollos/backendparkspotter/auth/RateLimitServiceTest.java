package com.ucentral.desarrollos.backendparkspotter.auth;

import com.ucentral.desarrollos.backendparkspotter.auth.security.RateLimitService;
import com.ucentral.desarrollos.backendparkspotter.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bloqueo temporal de login por intentos fallidos.
 */
class RateLimitServiceTest {

    private static final String KEY = "browser:user@parkspotter.co";

    @Test
    void allowsAttempts_UntilTheMaximumIsReached() {
        RateLimitService service = new RateLimitService(3, Duration.ofMinutes(15), Duration.ofMinutes(10));

        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThat(service.isLocked(KEY)).isFalse();
        assertThatCode(() -> service.assertAllowed(KEY)).doesNotThrowAnyException();
    }

    @Test
    void locksTheKey_AfterMaxFailures() {
        RateLimitService service = new RateLimitService(3, Duration.ofMinutes(15), Duration.ofMinutes(10));

        service.recordFailure(KEY);
        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThat(service.isLocked(KEY)).isTrue();
        assertThatThrownBy(() -> service.assertAllowed(KEY)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void lockIsPerKey_OtherClientsOrEmailsAreNotAffected() {
        RateLimitService service = new RateLimitService(1, Duration.ofMinutes(15), Duration.ofMinutes(10));

        service.recordFailure(KEY);

        assertThat(service.isLocked("android:user@parkspotter.co")).isFalse();
        assertThat(service.isLocked("browser:otro@parkspotter.co")).isFalse();
    }

    @Test
    void successfulLogin_ClearsPreviousFailures() {
        RateLimitService service = new RateLimitService(2, Duration.ofMinutes(15), Duration.ofMinutes(10));

        service.recordFailure(KEY);
        service.recordSuccess(KEY);
        service.recordFailure(KEY);

        assertThat(service.isLocked(KEY)).isFalse();
    }

    @Test
    void lockExpires_AfterLockoutDuration() {
        RateLimitService service = new RateLimitService(1, Duration.ZERO, Duration.ofMinutes(10));

        service.recordFailure(KEY);

        assertThat(service.isLocked(KEY)).isFalse();
        assertThatCode(() -> service.assertAllowed(KEY)).doesNotThrowAnyException();
    }

    @Test
    void failuresOutsideTheWindow_AreForgotten() {
        // Ventana que ya nació vencida: cada fallo abre una ventana nueva, así que nunca se acumulan dos
        RateLimitService service = new RateLimitService(2, Duration.ofMinutes(15), Duration.ofSeconds(-1));

        service.recordFailure(KEY);
        service.recordFailure(KEY);

        assertThat(service.isLocked(KEY)).isFalse();
    }

    @Test
    void unknownKey_IsNotLocked() {
        RateLimitService service = new RateLimitService(3, Duration.ofMinutes(15), Duration.ofMinutes(10));

        assertThat(service.isLocked("nunca-visto")).isFalse();
    }
}
