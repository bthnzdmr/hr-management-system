package com.proje.employee.dto;

import com.proje.employee.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserCreateRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        // Uzunluk tek gercek koruma: karakter cesitliligi zorunlulugu
        // kullanicilari "Parola1!" gibi tahmin edilebilir kaliplara iter.
        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters")
        String password,

        @NotEmpty(message = "At least one role is required")
        Set<Role> roles,

        // Opsiyonel: sistem hesaplarinin personel kaydi olmayabilir.
        Long employeeId
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
        return "UserCreateRequest[email=" + email
                + ", password=***, roles=" + roles
                + ", employeeId=" + employeeId + "]";
    }
}
