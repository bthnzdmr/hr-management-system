package com.proje.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String newPassword
) {

    /** Ne jeton ne parola metinsel temsile girer; gerekcesi PasswordChangeRequest'te. */
    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=***, newPassword=***]";
    }
}
