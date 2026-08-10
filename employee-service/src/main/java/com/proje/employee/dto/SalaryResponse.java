package com.proje.employee.dto;

import java.math.BigDecimal;

/**
 * Maas yalnizca bu cevapta doner; genel personel cevabinda yer almaz.
 * Boylece USER rolundeki istemciler (Notification Service dahil) maasi hic gormez.
 */
public record SalaryResponse(Long employeeId, BigDecimal salary) {
}
