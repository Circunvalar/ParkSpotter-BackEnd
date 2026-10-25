package com.ucentral.desarrollos.backendparkspotter.shared.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    static final int MIN_LENGTH = 12;
    /** BCrypt solo usa los primeros 72 bytes; Spring Security rechaza contraseñas más largas. */
    static final int MAX_BYTES = 72;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        return value.length() >= MIN_LENGTH
                && value.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES
                && value.equals(value.strip())
                && value.chars().anyMatch(Character::isLetter)
                && value.chars().anyMatch(Character::isDigit);
    }
}
