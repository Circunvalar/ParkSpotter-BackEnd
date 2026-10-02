package com.ucentral.desarrollos.backendparkspotter.auth.dto;

import com.ucentral.desarrollos.backendparkspotter.shared.validation.ValidPassword;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank @Size(max = 72) String currentPassword,
        @NotBlank @ValidPassword String newPassword
) {

    @AssertTrue(message = "la nueva contraseña debe ser distinta de la actual")
    public boolean isNewPasswordDifferentFromCurrent() {
        return currentPassword == null || newPassword == null || !currentPassword.equals(newPassword);
    }
}
