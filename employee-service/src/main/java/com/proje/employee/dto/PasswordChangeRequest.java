package com.proje.employee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Parola degistirme.
 *
 * Mevcut parola ZORUNLU: oturum acilmis bir tarayiciyi ele geciren biri, parolayi
 * bilmeden yenisini belirleyip hesabi kalicilastirabilirdi. Jeton "bu kisi giris
 * yapmisti" der, "bu kisi su anda parolayi biliyor" demez.
 */
public record PasswordChangeRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String newPassword
) {
}
