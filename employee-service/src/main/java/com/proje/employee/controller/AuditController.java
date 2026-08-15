package com.proje.employee.controller;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.dto.AuditEntryResponse;
import com.proje.employee.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "Audit", description = "Denetim izi. Yalnizca okunur; silme veya duzeltme ucu yoktur.")
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    // Siralama parametreye baglanmaz: gizli bir alana gore siralamak degeri
    // gostermeden bilgi sizdirir.
    @GetMapping
    @Operation(summary = "Denetim izi",
            description = "Kim, ne zaman, neyi degistirdi. Ucret TUTARI kayda hic yazilmaz.")
    public Page<AuditEntryResponse> list(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @PageableDefault(size = 25) Pageable pageable) {

        return auditService.search(actor, action, targetType, since, pageable);
    }
}
