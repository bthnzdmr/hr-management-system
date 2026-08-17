package com.proje.employee.dto;

import com.proje.employee.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Hesap acma istegi. PAROLA TASIMAZ.
 *
 * Parolayi hesabi acan kisi belirleseydi o parolayla giris yapip kullanicinin
 * kimligine burunebilir ve denetim izinde bu ayirt edilemezdi -- gorevler
 * ayriliginin tam da kapatmasi gereken sey. Bunun yerine kullaniciya davet
 * baglantisi gider ve parolayi YALNIZCA sahibi bilir.
 */
public record UserCreateRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must be at most 255 characters")
        String email,

        @NotEmpty(message = "At least one role is required")
        Set<Role> roles,

        // Opsiyonel: sistem hesaplarinin personel kaydi olmayabilir.
        Long employeeId
) {

}
