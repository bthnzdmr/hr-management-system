package com.proje.employee.dto;

import com.proje.employee.entity.Role;

import java.time.Instant;
import java.util.Set;

/**
 * Hesap bilgisi.
 *
 * Parola ozeti BILEREK yok. Bir kez cevaba eklenirse her istemciye, her loga ve
 * her tarayici gecmisine girer; DTO kullanmanin sebeplerinden biri tam da budur.
 */
public record UserResponse(
        Long id,
        String email,
        Set<Role> roles,
        boolean active,
        Long employeeId,
        String employeeFullName,
        Instant createdAt
) {
}
