package com.proje.employee.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeCreateRequest(
        String firstName,
        String lastName,
        String email,
        String phone,
        Long departmentId,
        Long managerId,
        String jobTitle,
        LocalDate hireDate,
        BigDecimal salary
) {
}
