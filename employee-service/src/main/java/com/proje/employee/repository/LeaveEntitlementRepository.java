package com.proje.employee.repository;

import com.proje.employee.entity.LeaveEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LeaveEntitlementRepository extends JpaRepository<LeaveEntitlement, Long> {

    Optional<LeaveEntitlement> findByEmployeeIdAndYear(Long employeeId, int year);

    /**
     * O yil KULLANILAN ve REZERVE edilen yillik izin gunleri, tek sorguda.
     *
     * Iki ayri sorgu yazilsaydi ikisi ayni ani gormeyebilirdi: arada onaylanan
     * bir talep hem "bekleyen" hem "kullanilan" sayilmaz, ama toplam yanlis
     * cikardi. Panelde ayni karar bir kez verilmisti.
     *
     * BEKLEYEN de dusulur. Dusulmeseydi 2 gunu kalan biri uc ayri 2 gunluk
     * talep acabilir ve ucu de onaylanabilir gorunurdu; mevcut EXCLUDE kisiti
     * yalnizca TARIH cakismasini engelliyor, hakkin asilmasini degil.
     *
     * Gun sayimi `end - start + 1`: son gun dahil, `LeaveRequestResponse` ile
     * ayni kural. Iki yerde farkli hesaplanmasi, birinin geride kalmasi olurdu.
     *
     * Yil, BASLANGIC tarihine gore atanir. Yil sinirini asan bir izin tek yila
     * yazilir; bolmek daha dogru olurdu ama bugun boyle bir kayit yok ve
     * olmayan bir problem icin karmasa eklenmiyor.
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(end_date - start_date + 1)
                         FILTER (WHERE status = 'APPROVED'), 0) AS used,
                COALESCE(SUM(end_date - start_date + 1)
                         FILTER (WHERE status = 'PENDING'), 0)  AS reserved
            FROM leave_request
            WHERE employee_id = :employeeId
              AND leave_type = 'ANNUAL'
              AND EXTRACT(YEAR FROM start_date) = :year
            """, nativeQuery = true)
    AnnualLeaveUsage findAnnualUsage(@Param("employeeId") Long employeeId, @Param("year") int year);

    /** Tek satirlik sonuc; `FILTER` JPQL'de yok, bu yuzden native. */
    interface AnnualLeaveUsage {
        int getUsed();

        int getReserved();
    }
}
