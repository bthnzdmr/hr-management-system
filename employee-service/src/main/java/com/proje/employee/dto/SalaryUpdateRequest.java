package com.proje.employee.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SalaryUpdateRequest(

        // NotNull zorunlu: bu uc yalnizca maas icin var, bos govde anlamsizdir.
        // Maasi temizlemek ayri bir islemdir ve bu uctan yapilmaz.
        @NotNull(message = "Salary is required")
        @DecimalMin(value = "0.00", message = "Salary must not be negative")
        @Digits(integer = 10, fraction = 2, message = "Salary must have at most 10 integer and 2 fraction digits")
        BigDecimal salary
) {
}
