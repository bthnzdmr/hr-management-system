package com.proje.employee.repository;

import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.time.LocalDate;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /**
     * Sinirsizligi ifade eden uclar.
     *
     * LocalDate.MIN/MAX KULLANILAMAZ: yillari +-999999999 ve PostgreSQL'in
     * DATE araligina sigmaz -- surucu sorguyu dusuruyor. Olculdu.
     */
    LocalDate BEGINNING_OF_TIME = LocalDate.of(1, 1, 1);
    LocalDate END_OF_TIME = LocalDate.of(9999, 12, 31);

    /** Listeleme sorgusu. */
    @Query(value = """
            SELECT l FROM LeaveRequest l
            LEFT JOIN FETCH l.employee e
            LEFT JOIN FETCH l.decidedBy
            LEFT JOIN FETCH l.createdBy
            WHERE (:statuses IS NULL OR l.status IN :statuses)
              AND (:employeeIds IS NULL OR e.id IN :employeeIds)
              AND (:departmentId IS NULL OR e.department.id = :departmentId)
              AND l.startDate <= :until AND l.endDate >= :from
            """,
            countQuery = """
            SELECT count(l) FROM LeaveRequest l
            WHERE (:statuses IS NULL OR l.status IN :statuses)
              AND (:employeeIds IS NULL OR l.employee.id IN :employeeIds)
              AND (:departmentId IS NULL OR l.employee.department.id = :departmentId)
              AND l.startDate <= :until AND l.endDate >= :from
            """)
    /**
     * Tarihler NULL GECILMEZ, notr deger gecilir.
     *
     * ":x IS NULL OR ..." kalibi tarihlerde bir kez patlamisti: turu
     * belirtilmemis bir NULL'u PostgreSQL bytea sanip sorguyu dusuruyordu.
     * Cagiran taraf sinirsizligi LocalDate.MIN/MAX ile ifade eder.
     *
     * Aralik ORTUSMEYE bakar: "bu hafta kim izinli" sorusunun cevabi, izni o
     * hafta BASLAYANLAR degil, o haftaya DENK GELENLERDIR.
     */
    Page<LeaveRequest> search(@Param("statuses") Collection<LeaveStatus> statuses,
                             @Param("employeeIds") Collection<Long> employeeIds,
                             @Param("departmentId") Long departmentId,
                             @Param("from") LocalDate from,
                             @Param("until") LocalDate until,
                             Pageable pageable);

    /** Tek kayit; kapsam kontrolu icin personel de yuklenir. */
    @Query("SELECT l FROM LeaveRequest l LEFT JOIN FETCH l.employee LEFT JOIN FETCH l.decidedBy LEFT JOIN FETCH l.createdBy WHERE l.id = :id")
    Optional<LeaveRequest> findByIdWithEmployee(@Param("id") Long id);

    /**
     * Ornek verinin daha once tohumlanip tohumlanmadigini soyler.
     *
     * `Containing` sart: tohumlanan notlarin bir kismi kullaniciya anlamli bir
     * metin de tasiyor ve isaret parantez icinde sonuna ekleniyor. Tam
     * esitlik arasaydik yalnizca notsuz kayitlar bulunur, tohum her acilista
     * tekrar calisir ve dislama kisiti uygulamayi dusururdu.
     */
    boolean existsByNoteContaining(String fragment);
}
