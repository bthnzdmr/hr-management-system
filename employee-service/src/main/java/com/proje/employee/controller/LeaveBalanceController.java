package com.proje.employee.controller;

import com.proje.employee.dto.LeaveBalanceResponse;
import com.proje.employee.service.AccessScope;
import com.proje.employee.service.AccessScopeResolver;
import com.proje.employee.service.LeaveBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

/**
 * Yillik izin bakiyesi.
 *
 * NEDEN `/api/leave-balances`, `/api/employees/{id}/leave-balance` DEGIL?
 *
 * Ikincisi olsaydi mevcut `/api/employees/**` okuma kuralinin ONUNE bir kural
 * yazmak gerekirdi ve Spring Security ILK eslesen kurali uygular -- sonra
 * yazilsaydi bakiye, izin verisini gormemesi gereken SYSTEM_ADMIN ve
 * PAYROLL_SPECIALIST'e de acik kalirdi. Ayni tuzak maas ucunde bir kez
 * yasandi; org chart ucunda ayri yol tam bu yuzden secilmisti.
 */
@RestController
@RequestMapping("/api/leave-balances")
@Tag(name = "Leave balance", description = "Yillik izin hakki ve kalan gun")
public class LeaveBalanceController {

    private final LeaveBalanceService balances;
    private final AccessScopeResolver scopes;

    public LeaveBalanceController(LeaveBalanceService balances, AccessScopeResolver scopes) {
        this.balances = balances;
        this.scopes = scopes;
    }

    @GetMapping("/{employeeId}")
    @Operation(summary = "Bir personelin yillik izin bakiyesi",
            description = """
                    Yil verilmezse icinde bulunulan yil kullanilir.

                    YALNIZCA yillik izin sayilir; hastalik, ucretsiz ve ebeveyn
                    izni ayri haklardir ve bu bakiyeden dusmez.
                    """)
    @ApiResponse(responseCode = "404",
            description = "Kayit yok VEYA cagiranin kapsami disinda -- ikisi ayirt edilmez")
    public LeaveBalanceResponse get(@PathVariable Long employeeId,
                                    @RequestParam(required = false) Integer year,
                                    Principal principal) {

        AccessScope scope = scopes.resolve(principal);

        return balances.balanceFor(employeeId,
                year != null ? year : LocalDate.now().getYear(),
                scope);
    }
}
