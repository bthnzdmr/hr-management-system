package com.proje.employee.export;

import com.proje.employee.audit.AuditDetail;

import java.util.List;

/**
 * Ice aktarmanin sonucu.
 *
 * @param imported yazilan kayit sayisi; reddedilen dosyada DAIMA sifir
 * @param errors   satir satir gerekce; bos ise dosya kabul edilmistir
 */
public record ImportReport(int imported, List<RowError> errors) implements AuditDetail {

    /**
     * @param line   dosyadaki satir numarasi (baslik 1'dir)
     * @param reason kullanicinin okuyacagi gerekce
     */
    public record RowError(int line, String reason) {
    }

    public static ImportReport accepted(int imported) {
        return new ImportReport(imported, List.of());
    }

    public static ImportReport rejected(List<RowError> errors) {
        return new ImportReport(0, errors);
    }

    public boolean isRejected() {
        return !errors.isEmpty();
    }

    @Override
    public String auditDetail() {
        return isRejected()
                ? "Import rejected · " + errors.size() + " invalid row(s)"
                : "Imported " + imported + (imported == 1 ? " employee" : " employees");
    }
}
