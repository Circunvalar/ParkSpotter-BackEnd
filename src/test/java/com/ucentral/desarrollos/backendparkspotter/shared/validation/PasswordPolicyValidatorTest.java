package com.ucentral.desarrollos.backendparkspotter.shared.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "Password123!",
            "contraseña segura 2026",
            "123456789abc",
            "ÑandúVeloz#2026"
    })
    void acceptsStrongPasswords(String password) {
        assertThat(validator.isValid(password, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Corta1!",                // menos de 12 caracteres
            "SoloLetrasSinNumeros",   // sin dígitos
            "123456789012345",        // sin letras
            " Password12345",         // espacio al inicio
            "Password12345 "          // espacio al final
    })
    void rejectsWeakPasswords(String password) {
        assertThat(validator.isValid(password, null)).isFalse();
    }

    @Test
    void rejectsPasswordsLongerThan72Bytes_EvenIfUnder72Characters() {
        // 40 caracteres, pero cada "ñ" ocupa 2 bytes en UTF-8: 76 bytes > límite de BCrypt
        String multibyte = "ñ".repeat(36) + "a1b2";

        assertThat(multibyte.length()).isLessThan(72);
        assertThat(validator.isValid(multibyte, null)).isFalse();
    }

    @Test
    void nullIsLeftToNotBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
