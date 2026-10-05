package com.ucentral.desarrollos.backendparkspotter.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Política de contraseñas para registro y cambio de contraseña:
 * entre 12 y 72 caracteres, máximo 72 bytes en UTF-8 (límite de BCrypt),
 * al menos una letra y un número, y sin espacios al inicio o al final.
 * Null se considera válido: combinar con @NotBlank.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "debe tener entre 12 y 72 caracteres, al menos una letra y un número, y no empezar ni terminar con espacios";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
