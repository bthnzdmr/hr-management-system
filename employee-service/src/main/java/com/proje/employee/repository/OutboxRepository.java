package com.proje.employee.repository;

import com.proje.employee.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    /**
     * Yayinlanmis ve artik gerekmeyen satirlari siler.
     *
     * Her personel islemi bir satir yaziyordu ve HICBIRI silinmiyordu: yigin,
     * yedekler ve VACUUM maliyeti dogrusal ve kalici olarak buyur. Kismi index
     * (WHERE published_at IS NULL) relay'in SORGUSUNU hizli tutar ama tablonun
     * kendisini kucultmez.
     *
     * Kardes tablo refresh_token icin bu temizlik zaten vardi; outbox
     * atlanmisti. Tasarim notlari TERK EDILMIS satirlari tartisiyor ama
     * BASARIYLA yayinlanmislari hic ele almiyordu.
     *
     * published_at IS NOT NULL sart: yayinlanmamis satir, gonderilmeyi bekleyen
     * bir olaydir ve silinmesi VERI KAYBI olur.
     */
    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.publishedAt IS NOT NULL AND e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);

    /** Yayinlanmayi bekleyen olay sayisi -- metrik olarak izlenir. */
    @Query("SELECT count(e) FROM OutboxEvent e WHERE e.publishedAt IS NULL")
    long countPending();

    /**
     * EN ESKI yayinlanmamis olayin olusturulma zamani.
     *
     * Gecmis gecikmelerin ortalamasi degil, SU ANKI birikim olculur: "relay ne
     * kadar geride" sorusunun cevabi budur ve eyleme donusen sayi odur.
     */
    @Query("SELECT min(e.createdAt) FROM OutboxEvent e WHERE e.publishedAt IS NULL")
    Instant oldestPendingCreatedAt();
}
