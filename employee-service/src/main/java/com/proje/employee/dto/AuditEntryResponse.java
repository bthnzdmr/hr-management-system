package com.proje.employee.dto;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.AuditEntry;

import java.time.Instant;

/** Denetim izinde tek satir. Ucret tutari ize hic yazilmaz. */
public record AuditEntryResponse(
        Long id,
        String actor,
        AuditAction action,
        String targetType,
        String targetId,
        String detail,
        String correlationId,
        Instant occurredAt
) {

    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.getId(),
                entry.getActor(),
                entry.getAction(),
                entry.getTargetType(),
                entry.getTargetId(),
                entry.getDetail(),
                entry.getCorrelationId(),
                entry.getOccurredAt());
    }
}
