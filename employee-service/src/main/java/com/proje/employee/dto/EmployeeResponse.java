package com.proje.employee.dto;

import java.time.LocalDate;

public record EmployeeResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        String phone,
        Long departmentId,
        String departmentName,
        Long managerId,
        String jobTitle,
        LocalDate hireDate,
        boolean active
) {
}
