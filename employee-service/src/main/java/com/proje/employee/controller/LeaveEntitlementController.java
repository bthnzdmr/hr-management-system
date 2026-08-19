package com.proje.employee.controller;

import com.proje.employee.dto.LeaveEntitlementRequest;
import com.proje.employee.dto.LeaveEntitlementResponse;
import com.proje.employee.service.LeaveEntitlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Yillik izin hakkinin ELLE belirlenmesi.
 *
 * OKUMA UCU YOK ve bu bilincli: `/api/leave-balances/{employeeId}` zaten
 * hakki, devreden gunu ve kaynagi (`GRANTED` / `DEFAULT`) donduruyor. Ikinci
 * bir okuma ucu ayni bilgiyi iki yerde tutmak olurdu ve ikisi zamanla
 * birbirinden ayrilirdi.
 *
 * Yol AYRI (`/api/leave-entitlements`), `/api/employees/{id}/entitlement`
 * degil: ikincisi olsaydi mevcut `/api/employees/**` kuralinin ONUNE bir kural
 * yazmak gerekirdi ve Spring Security ILK eslesen kurali uygular. Ayni tuzak
 * maas ucunde bir kez yasandi.
 */
@RestController
@RequestMapping("/api/leave-entitlements")
@Tag(name = "Leave entitlement", description = "Yillik izin hakkinin elle belirlenmesi")
public class LeaveEntitlementController {

    private final LeaveEntitlementService entitlements;

    public LeaveEntitlementController(LeaveEntitlementService entitlements) {
        this.entitlements = entitlements;
    }

    @PutMapping("/{employeeId}/{year}")
    @Operation(summary = "Bir personelin bir yila ait hakkini belirler",
            description = """
                    Tahakkuk isi eksik satirlari kidem merdivenine gore yazar;
                    bu uc onun UZERINE yazar ve is bir daha dokunmaz.

                    Idempotent: butun degerler birden gonderilir. Artimli bir uc
                    (gun ekle / gun cikar) iki es zamanli istekte birbirinin
                    uzerine yazabilirdi.
                    """)
    @ApiResponse(responseCode = "404", description = "Boyle bir personel yok")
    public LeaveEntitlementResponse set(@PathVariable Long employeeId,
                                        @PathVariable int year,
                                        @Valid @RequestBody LeaveEntitlementRequest request) {

        return entitlements.set(employeeId, year, request);
    }
}
