package com.proje.employee.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Iki yone de calisir: acma ve kapatma.
 *
 * Personel durum ucuyla ayni gerekce -- DELETE fiili yanlis olurdu, kayit
 * silinmiyor bir alan degisiyor; ayrica DELETE'in geri donusu olmazdi.
 */
public record DepartmentStatusRequest(
        @NotNull(message = "Active flag is required")
        Boolean active
) {
}
