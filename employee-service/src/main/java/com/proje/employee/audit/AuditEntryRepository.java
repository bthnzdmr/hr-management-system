package com.proje.employee.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

/**
 * Denetim izi deposu.
 *
 * Yalnizca yazma ve okuma var; guncelleme ve silme METODU BILEREK YOK.
 * JpaRepository silme metotlarini miras verir ama uygulama kodundan
 * cagrilmaz -- saklama politikasi geldiginde ayri bir migration isi olur.
 */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    /** Bir kaydin gecmisi: "bu kullanicinin rolleri ne zaman, kim tarafindan degisti". */
    Page<AuditEntry> findByTargetTypeAndTargetIdOrderByOccurredAtDesc(
            String targetType, String targetId, Pageable pageable);

    /** Bir hesabin yaptiklari: ele gecirilme suphesinde ilk sorgu. */
    Page<AuditEntry> findByActorOrderByOccurredAtDesc(String actor, Pageable pageable);

    // Hicbir parametre NULL gelmez: bos bir parametrenin tipi olmadigi icin
    // PostgreSQL "IS NULL OR ..." kalibini reddediyor. Bos filtreler notr
    // degere cevrilir. Sira disariya acilmaz -- siralama bir yan kanaldir.
    @Query("""
            SELECT a FROM AuditEntry a
            WHERE lower(a.actor) LIKE lower(concat('%', :actor, '%'))
              AND a.action IN :actions
              AND (:targetType = '' OR lower(a.targetType) = lower(:targetType))
              AND a.occurredAt >= :since
            ORDER BY a.occurredAt DESC
            """)
    Page<AuditEntry> search(@Param("actor") String actor,
                            @Param("actions") Collection<AuditAction> actions,
                            @Param("targetType") String targetType,
                            @Param("since") Instant since,
                            Pageable pageable);
}