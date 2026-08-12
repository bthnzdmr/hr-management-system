package com.proje.employee.dto;

import com.proje.employee.entity.TerminationReason;
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
        Boolean active,

        /**
         * Pasiflestirirken ZORUNLU, aktiflestirirken yok sayilir.
         *
         * Varsayilan bir deger atanmadi: "OTHER" ile doldurmak veriyi
         * kirletirdi ve devir oraninin en anlamli kirilimi olan "istege bagli
         * mi zorunlu mu" ayrimi kaybolurdu. Eksikse istek reddedilir.
         */
        TerminationReason terminationReason
) {
}
