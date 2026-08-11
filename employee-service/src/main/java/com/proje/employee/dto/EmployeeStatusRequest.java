package com.proje.employee.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Personelin aktiflik durumu.
 *
 * DELETE yerine bu uc kullanilir: kayit silinmiyor, durumu degisiyor.
 * DELETE "sildim" der; oysa veri duruyor ve geri alinabiliyor.
 * Tek uc iki yonu de yonettigi icin dogasi geregi idempotenttir.
 */
public record EmployeeStatusRequest(

        @NotNull(message = "Active flag is required")
        Boolean active
) {
}
