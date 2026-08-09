package com.proje.employee.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EmployeeCreateRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @Size(max = 20, message = "Phone must not exceed 20 characters")
        String phone,

        @NotNull(message = "Department is required")
        Long departmentId,

        Long managerId,

        @NotBlank(message = "Job title is required")
        @Size(max = 100, message = "Job title must not exceed 100 characters")
        String jobTitle,

        // Gelecek tarih kisitlanmadi: sozlesmesi imzalanmis, ileride baslayacak
        // personel kaydi acmak gercek bir ihtiyactir.
        @NotNull(message = "Hire date is required")
        LocalDate hireDate,

        // @DecimalMin, @PositiveOrZero ile ayni kurali ifade eder ama OpenAPI
        // semasina da yansir; @PositiveOrZero yansimiyor.
        @DecimalMin(value = "0.00", message = "Salary must not be negative")
        @Digits(integer = 10, fraction = 2, message = "Salary must have at most 10 integer and 2 fraction digits")
        BigDecimal salary
) {
}
