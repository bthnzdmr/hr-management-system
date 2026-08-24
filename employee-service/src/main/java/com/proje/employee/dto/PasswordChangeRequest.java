package com.proje.employee.dto;

import jakarta.validation.constraints.NotBlank;

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
        String newPassword
) {

    /**
     * Parola METINSEL TEMSILE HIC GIRMEZ.
     *
     * record'un uretilmis toString'i butun bilesenleri basar. Bu, sirri
     * tasiyan bir nesne icin sessiz bir sizinti kaynagidir: log satiri,
     * istisna mesaji, hata ayiklama ciktisi ve denetim kaydi -- hepsi
     * toString cagirir.
     *
     * OLCULDU: denetim izi eklendiginde bu tam olarak yasandi; acilan
     * hesabin parolasi audit_entry.detail kolonuna DUZ METIN yazildi.
     * Kok neden aspect degildi, record'un kendisiydi -- bu yuzden duzeltme
     * de burada: sirri hic uretmeyen bir temsil, onu her yerde korur.
     */
    @Override
    public String toString() {
        return "PasswordChangeRequest[" + "currentPassword=***" + ", " + "newPassword=***" + "]";
    }
}
