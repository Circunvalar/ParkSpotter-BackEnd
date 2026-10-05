package com.ucentral.desarrollos.backendparkspotter.auth;

import com.ucentral.desarrollos.backendparkspotter.auth.dto.ChangePasswordRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LoginRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.LogoutRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RefreshRequest;
import com.ucentral.desarrollos.backendparkspotter.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidationTestSupport.invalidFields;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Criterios de validación de los DTOs de autenticación, campo por campo.
 */
class AuthRequestValidationTest {

    private static final String VALID_PASSWORD = "Password123!";

    @Test
    void validRegister_HasNoErrors() {
        assertThat(invalidFields(new RegisterRequest("nuevo@parkspotter.co", VALID_PASSWORD))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "sin-arroba", "a@b", "a@b.", "espacio @dominio.com"})
    void register_RejectsInvalidEmails(String email) {
        assertThat(invalidFields(new RegisterRequest(email, VALID_PASSWORD))).contains("email");
    }

    @Test
    void register_RejectsEmailsLongerThan254() {
        String longEmail = "a".repeat(250) + "@x.co";

        assertThat(invalidFields(new RegisterRequest(longEmail, VALID_PASSWORD))).contains("email");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corta1", "sololetrassinnumeros", "123456789012345"})
    void register_RejectsWeakPasswords(String password) {
        assertThat(invalidFields(new RegisterRequest("ok@parkspotter.co", password))).contains("password");
    }

    @Test
    void login_RequiresEmailAndPassword_AndLimitsPasswordTo72() {
        assertThat(invalidFields(new LoginRequest("", ""))).contains("email", "password");
        assertThat(invalidFields(new LoginRequest("ok@parkspotter.co", "x".repeat(73)))).contains("password");
        // En el login no se exige la política (solo se compara): una contraseña corta es válida como entrada
        assertThat(invalidFields(new LoginRequest("ok@parkspotter.co", "corta"))).isEmpty();
    }

    @Test
    void changePassword_NewMustFollowPolicyAndDifferFromCurrent() {
        assertThat(invalidFields(new ChangePasswordRequest("ActualPassword1", "NuevaPassword2"))).isEmpty();
        assertThat(invalidFields(new ChangePasswordRequest("ActualPassword1", "debil"))).contains("newPassword");
        assertThat(invalidFields(new ChangePasswordRequest("MismaPassword1", "MismaPassword1")))
                .contains("newPasswordDifferentFromCurrent");
        assertThat(invalidFields(new ChangePasswordRequest("", "NuevaPassword2"))).contains("currentPassword");
    }

    @Test
    void changePassword_WithMissingValues_OnlyReportsRequiredFields() {
        assertThat(invalidFields(new ChangePasswordRequest(null, null)))
                .containsExactlyInAnyOrder("currentPassword", "newPassword");
    }

    @Test
    void refreshAndLogout_RequireOpaqueTokenFormat() {
        assertThat(invalidFields(new RefreshRequest("abcDEF123_-xyz"))).isEmpty();
        assertThat(invalidFields(new RefreshRequest(""))).contains("refreshToken");
        assertThat(invalidFields(new RefreshRequest("token con espacios"))).contains("refreshToken");
        assertThat(invalidFields(new RefreshRequest("a".repeat(201)))).contains("refreshToken");
        assertThat(invalidFields(new LogoutRequest("<script>"))).contains("refreshToken");
    }
}
