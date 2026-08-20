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
import com.proje.employee.audit.AuditDetail;
import com.proje.employee.audit.AuditLabel;

public record UserResponse(
        Long id,
        String email,
        Set<Role> roles,
        boolean active,
        Long employeeId,
        String employeeFullName,
        Instant createdAt
) implements AuditLabel, AuditDetail {

    @Override
    public String auditLabel() {
        return email;
    }

    /**
     * Hesabin O ANKI hali.
     *
     * Uc eylem ayni cevabi donduruyor (acma, rol degisikligi, durum
     * degisikligi), bu yuzden cumle DURUM anlatir; fiili eylem etiketi soyler.
     *
     * Roller her zaman yaziliyor: rol degisikliginde izin en degerli kismi
     * odur ve "onceki hal" ayni hedefin bir onceki kaydinda durur.
     */
    @Override
    public String auditDetail() {
        String named = roles.stream()
                .map(Role::label)
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));

        String state = (active ? "Active" : "Disabled") + " · Roles: " + named;

        return employeeFullName == null ? state : state + " · Linked to " + employeeFullName;
    }
}
