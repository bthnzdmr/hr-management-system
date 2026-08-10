package com.proje.employee.repository;

import com.proje.employee.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    // FOR UPDATE SKIP LOCKED: secilen satirlar kilitlenir, ayni anda calisan
    // baska bir kopya bu satirlari beklemeden atlar ve kendine baskalarini alir.
    // JPQL bu sozdizimini desteklemedigi icin native sorgu.
    @Query(value = """
            SELECT * FROM outbox
            WHERE published_at IS NULL
              AND attempts < :maxAttempts
            ORDER BY created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPending(@Param("maxAttempts") int maxAttempts,
                                  @Param("batchSize") int batchSize);
}
