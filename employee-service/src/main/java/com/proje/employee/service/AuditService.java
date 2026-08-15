package com.proje.employee.service;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.AuditEntryRepository;
import com.proje.employee.dto.AuditEntryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;

/** Denetim izini okur. Silme veya guncelleme yoktur; iz eklemelidir. */
@Service
public class AuditService {

    private final AuditEntryRepository auditEntries;

    public AuditService(AuditEntryRepository auditEntries) {
        this.auditEntries = auditEntries;
    }

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> search(String actor, AuditAction action, String targetType,
                                           Instant since, Pageable pageable) {

        // Bos filtreler notr degere cevrilir; sorgu hicbir zaman NULL gormez.
        return auditEntries.search(
                        actor == null ? "" : actor.trim(),
                        action == null ? EnumSet.allOf(AuditAction.class) : EnumSet.of(action),
                        targetType == null ? "" : targetType.trim(),
                        since == null ? Instant.EPOCH : since,
                        pageable)
                .map(AuditEntryResponse::from);
    }
}
