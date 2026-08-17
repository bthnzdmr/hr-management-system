package com.proje.employee.event;

import java.time.Instant;

/**
 * Hesap olaylarinin sozlesmesi. EmployeeEvent gibi JSON'dur, ortak bir Java
 * modulu degil.
 *
 * <p>HAM jetonu tasir ve bu bilincli bir takastir: mail iceriginde tiklanabilir
 * bir baglanti olmasinin baska yolu yok. Bedeli, jetonun outbox satirinda duz
 * metin durmasidir -- tam da ozetleyerek kacindigimiz sey. Pencereyi kapatan
 * jetonun kisa omrudur: outbox 30 gun saklanir ama jeton 30 dakikada olur,
 * dolayisiyla eski bir yedegi okuyan kisi yalnizca olu jeton bulur.
 */
public record AccountEvent(

        String eventId,
        AccountEventType eventType,
        Instant occurredAt,

        Long userId,
        String email,
        String resetToken,
        Instant expiresAt
) {

    /**
     * Jeton METINSEL TEMSILE HIC GIRMEZ; ayni gerekce PasswordChangeRequest'te.
     *
     * Sizmis bir olay kaydi jetonu ele verirdi ve bu nesne tam da log satirina
     * dusmeye aday: relay basarisiz bir yayini hata mesajiyla birlikte basiyor.
     * Serilestirme Jackson'in isi, toString'in degil -- maskelemek mesaji
     * bozmaz.
     */
    @Override
    public String toString() {
        return "AccountEvent[eventId=" + eventId + ", eventType=" + eventType
                + ", userId=" + userId + ", resetToken=***, expiresAt=" + expiresAt + "]";
    }
}
