package com.proje.employee.dto;

import com.proje.employee.entity.TerminationReason;

import java.time.LocalDate;

import com.proje.employee.audit.AuditDetail;
import com.proje.employee.audit.AuditLabel;

public record EmployeeResponse(
        Long id,

        /**
         * Iyimser kilit surumu; istemci guncellemede AYNEN geri gonderir.
         *
         * Bu alan olmadan "bu kaydi ben actiktan sonra baskasi degistirdi mi"
         * sorusu cevaplanamaz: sunucu yalnizca son hali gorur.
         */
        Long version,

        String firstName,
        String lastName,
        String email,
        String phone,
        Long departmentId,
        String departmentName,
        Long managerId,
        // Yalnizca id donmek arayuzde "Yonetici: 42" demek olurdu. Bu alan
        // eklendigi anda findAllWithDepartment'a LEFT JOIN FETCH e.manager
        // zorunlu hale geldi: proxy'nin ID'sini okumak bedava, ADINI okumak
        // her satir icin ayri sorgu uretir (olculdu).
        String managerFullName,
        String jobTitle,
        LocalDate hireDate,
        boolean active,

        // Yalnizca pasif kayitlarda dolu. Personelin ne zaman ve neden
        // ayrildigi, devir oraninin dayandigi bilgidir.
        LocalDate terminatedAt,
        TerminationReason terminationReason
) implements AuditLabel, AuditDetail {

    @Override
    public String auditLabel() {
        return firstName + " " + lastName;
    }

    /**
     * Kaydin O ANKI hali.
     *
     * Ayni cevap tipi IKI eylemde birden kullaniliyor (olusturma ve durum
     * degisikligi), dolayisiyla cumle bir FIIL degil bir DURUM anlatir --
     * fiili eylem etiketi zaten soyluyor. "Yeniden aktiflestirildi" de bu
     * sayede dogru okunur: kayit yine aktif ve unvani yaziyor.
     */
    @Override
    public String auditDetail() {
        if (!active) {
            String reason = terminationReason == null ? "" : " (" + AuditDetail.humanise(terminationReason) + ")";
            return "Left on " + AuditDetail.day(terminatedAt) + reason;
        }

        String where = jobTitle + " in " + departmentName;

        return managerFullName == null ? where : where + " · reports to " + managerFullName;
    }

}
