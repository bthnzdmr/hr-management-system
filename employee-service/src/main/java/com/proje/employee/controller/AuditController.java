package com.proje.employee.controller;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.dto.AuditEntryResponse;
import com.proje.employee.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    // Istemcinin siralamasi YOK SAYILIR. Yorum eskiden bunu iddia ediyordu ama
    // Pageable siralamayi da tasiyor ve kod iddiayi tutmuyordu; sorgu zaten
    // occurredAt DESC donuyor.
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

        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        return auditService.search(actor, action, targetType, since, unsorted);
    }
}
