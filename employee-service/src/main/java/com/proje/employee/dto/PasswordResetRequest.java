package com.proje.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Sifirlama baglantisi istegi.
 *
 * Cevap hesabin VAR OLUP OLMADIGINI soylemez; dogrulama yalnizca bicimi
 * kontrol eder.
 */
public record PasswordResetRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        String email
) {
}
