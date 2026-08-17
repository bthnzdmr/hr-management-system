package com.proje.employee.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Maas bu istegin PARCASI DEGILDIR.
 *
 * Tasiyor olsaydi, maasi okuyamayan bir istemci onu her guncellemede null
 * gonderip silerdi. Maas kendi ucu uzerinden yonetilir: /api/employees/{id}/salary
 */
public record EmployeeUpdateRequest(

        /**
         * Istemcinin DUZENLEMEYE BASLADIGI surum.
         *
         * Entity'deki `@Version` iki es zamanli transaction'i yakalar ama asil
         * problem daha uzun bir pencerede: kullanici formu acar, baskasi
         * kaydeder, sonra o kaydeder. Arada dakikalar olabilir ve iki
         * transaction hic cakismaz. Bu alan o pencereyi kapatir.
         */
        @NotNull(message = "Version is required")
        Long version,

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

        @NotNull(message = "Hire date is required")
        LocalDate hireDate
) {
}
