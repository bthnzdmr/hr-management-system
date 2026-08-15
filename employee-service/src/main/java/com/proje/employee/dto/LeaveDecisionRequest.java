package com.proje.employee.dto;

import com.proje.employee.entity.LeaveStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Bir izin istegini sonuclandirma.
 *
 * <p>Tek bir uc uc yone birden calisir. Ayri "onayla", "reddet" ve "iptal et"
 * uclari acilsaydi yeni bir durum eklemek yeni bir uc gerektirirdi; govde
 * durumu tasidigi icin gerekmiyor. Ayni karar personel ve departman durum
 * uclarinda da verilmisti.
 */
public record LeaveDecisionRequest(

        @NotNull(message = "Status is required")
        LeaveStatus status,

        @Size(max = 500, message = "Note cannot be longer than 500 characters")
        String note
) {

    /**
     * PENDING'e geri donulemez.
     *
     * Bir istegi yeniden "bekliyor" yapmak, karar bilgisini silmek demektir ve
     * denetim izinde "ne zaman ve kim" sorusunu cevapsiz birakirdi.
     */
    @AssertTrue(message = "A request can only be approved, rejected or cancelled")
    public boolean isFinalStatus() {
        return status == null || status != LeaveStatus.PENDING;
    }
}
