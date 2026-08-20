package com.proje.employee.export;

import com.proje.employee.audit.AuditDetail;

/**
 * Uretilen dosya ve KAC satir tasidigi.
 *
 * Satir sayisi yalnizca bir sayi degil, denetim izinin eksik parcasiydi: "kim
 * butun dizini indirdi" sorusunun cevabi suzgeclerden dolayli olarak
 * cikariliyordu, dogrudan yazili degildi.
 *
 * @param rowCount baslik satiri HARIC; kisi sayisi
 */
public record ExportResult(String csv, int rowCount, String search, Boolean active)
        implements AuditDetail {

    @Override
    public String auditDetail() {
        String what = rowCount + (rowCount == 1 ? " employee record" : " employee records");
        String filter = filterDescription();

        return "Exported " + what + (filter.isEmpty() ? " (no filter)" : " · " + filter);
    }

    private String filterDescription() {
        StringBuilder parts = new StringBuilder();

        if (search != null && !search.isBlank()) {
            parts.append("Search: ").append(search.trim());
        }
        if (active != null) {
            if (parts.length() > 0) {
                parts.append(" · ");
            }
            parts.append(active ? "Active only" : "Left only");
        }

        return parts.toString();
    }
}
