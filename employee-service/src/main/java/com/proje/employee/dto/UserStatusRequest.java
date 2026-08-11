package com.proje.employee.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Personelinkiyle ayni bicimde ama ayri bir tip.
 *
 * Ortak bir tipe indirgemek hesap yonetimini personel yonetimine baglardi:
 * birinin sozlesmesi degistiginde digeri de degismek zorunda kalirdi.
 */
public record UserStatusRequest(
        @NotNull(message = "Active flag is required")
        Boolean active
) {
}
