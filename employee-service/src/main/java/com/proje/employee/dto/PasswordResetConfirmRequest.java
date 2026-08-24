package com.proje.employee.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "Token is required")
        String token,

        @NotBlank(message = "New password is required")
        String newPassword
) {

    /** Ne jeton ne parola metinsel temsile girer; gerekcesi PasswordChangeRequest'te. */
    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=***, newPassword=***]";
    }
}
