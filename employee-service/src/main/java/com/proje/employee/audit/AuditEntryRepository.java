package com.proje.employee.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
