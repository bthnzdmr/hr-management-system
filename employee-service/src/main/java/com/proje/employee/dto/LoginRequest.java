package com.proje.employee.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "Email is required")
        String email,

        @NotBlank(message = "Password is required")
        String password
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
        return "LoginRequest[" + "email=" + email + ", " + "password=***" + "]";
    }
}
