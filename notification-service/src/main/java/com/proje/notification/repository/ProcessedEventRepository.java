package com.proje.notification.repository;

import com.proje.notification.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * Idempotency penceresini kapatan silme.
     *
     * Saklama suresi, bir mesajin broker'da bekleyebilecegi en uzun sureden
     * GUVENLE uzun olmalidir: satir erken silinirse gec teslim edilen bir olay
     * "hic islenmemis" gorunur ve ikinci bir mail gider.
     */
    @Modifying
    @Query("DELETE FROM ProcessedEvent p WHERE p.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
