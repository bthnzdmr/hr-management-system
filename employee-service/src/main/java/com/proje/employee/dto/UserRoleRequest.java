package com.proje.employee.dto;

import com.proje.employee.entity.Role;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * Rol kumesi KOMPLE gonderilir, tek tek eklenip cikarilmaz.
 *
 * "Rol ekle" ve "rol cikar" ayri uclar olsaydi, iki es zamanli istek birbirinin
 * uzerine yazabilir ve kimsenin istemedigi bir ara duruma dusulebilirdi.
 * Butun kumeyi gondermek istegi idempotent yapar.
 */
public record UserRoleRequest(
        @NotEmpty(message = "At least one role is required")
        Set<Role> roles
) {
}
