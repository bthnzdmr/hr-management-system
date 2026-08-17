package com.proje.employee.dto;

import com.proje.employee.entity.TerminationReason;

import java.time.LocalDate;

import com.proje.employee.audit.AuditLabel;

public record EmployeeResponse(
        Long id,

        /**
         * Iyimser kilit surumu; istemci guncellemede AYNEN geri gonderir.
         *
         * Bu alan olmadan "bu kaydi ben actiktan sonra baskasi degistirdi mi"
         * sorusu cevaplanamaz: sunucu yalnizca son hali gorur.
         */
        Long version,

        String firstName,
        String lastName,
        String email,
        String phone,
        Long departmentId,
        String departmentName,
        Long managerId,
        // Yalnizca id donmek arayuzde "Yonetici: 42" demek olurdu. Bu alan
        // eklendigi anda findAllWithDepartment'a LEFT JOIN FETCH e.manager
        // zorunlu hale geldi: proxy'nin ID'sini okumak bedava, ADINI okumak
        // her satir icin ayri sorgu uretir (olculdu).
        String managerFullName,
        String jobTitle,
        LocalDate hireDate,
        boolean active,

        // Yalnizca pasif kayitlarda dolu. Personelin ne zaman ve neden
        // ayrildigi, devir oraninin dayandigi bilgidir.
        LocalDate terminatedAt,
        TerminationReason terminationReason
) implements AuditLabel {

    @Override
    public String auditLabel() {
        return firstName + " " + lastName;
    }

}
