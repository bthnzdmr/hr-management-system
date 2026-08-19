package com.proje.employee.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Ik'nin bir personel-yil icin belirledigi yillik izin hakki.
 *
 * Butun degerler birden gonderilir, artimli degil: "gun ekle" gibi bir uc iki
 * es zamanli istekte birbirinin uzerine yazabilirdi.
 */
public record LeaveEntitlementRequest(

        @NotNull(message = "Entitled days are required")
        @Min(value = 0, message = "Entitled days cannot be negative")
        @Max(value = 365, message = "Entitled days cannot exceed 365")
        Integer entitledDays,

        @NotNull(message = "Carried over days are required")
        @Min(value = 0, message = "Carried over days cannot be negative")
        @Max(value = 365, message = "Carried over days cannot exceed 365")
        Integer carriedOverDays,

        /**
         * Elle verilen hakkin gerekcesi.
         *
         * Zorunlu: tahakkuk isinin yazdigi satir ile Ik'nin ELLE degistirdigi
         * satir ayni tabloda duruyor ve ayirt edilebilmeleri gerekiyor --
         * "neden bu kisinin hakki farkli" sorusunun cevabi burada.
         */
        @NotNull(message = "A reason is required")
        @Size(min = 1, max = 255, message = "Reason must be between 1 and 255 characters")
        String note
) {
}
