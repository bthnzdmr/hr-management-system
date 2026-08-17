package com.proje.employee.dto;

import com.proje.employee.entity.TerminationReason;

import java.time.LocalDate;

import com.proje.employee.audit.AuditLabel;

public record EmployeeResponse(
        Long id,
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
