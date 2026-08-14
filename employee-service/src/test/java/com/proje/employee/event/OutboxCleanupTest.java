package com.proje.employee.event;

import com.proje.employee.entity.OutboxEvent;
import com.proje.employee.repository.OutboxRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Saklama politikasi GERCEK veritabanina karsi sinanir.
 *
 * Silme sorgusunun dogru satirlari sectigi, taklit edilerek kanitlanamaz --
 * yanlis bir WHERE yazilsa bile mock test yesil kalirdi. Buradaki asil iddia
 * "yayinlanmamis satir SILINMEZ": o satir gonderilmeyi bekleyen bir olaydir ve
 * silinmesi veri kaybi olurdu.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OutboxCleanupTest {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Test onceki duruma bagimli olmamali. @DataJpaTest transaction icinde
        // calisip geri aldigi icin bu silme kalici degil.
        outboxRepository.deleteAllInBatch();
    }

    /**
     * created_at ve published_at'i ELLE geriye alir.
     *
     * @PrePersist createdAt'i "simdi" yapar ve markPublished() de oyle; eski
     * bir satiri taklit etmenin baska yolu yok.
     */
    private Long row(Instant publishedAt) {
        OutboxEvent event = new OutboxEvent(UUID.randomUUID(), "CREATED",
                "employee.created", "{}", null);
        outboxRepository.saveAndFlush(event);

        entityManager.createNativeQuery(
                        "UPDATE outbox SET published_at = :p WHERE id = :id")
                .setParameter("p", publishedAt)
                .setParameter("id", event.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        return event.getId();
    }

    @Test
    @DisplayName("Removes rows published before the cutoff")
    void removesOldPublishedRows() {
        Long old = row(Instant.now().minus(40, ChronoUnit.DAYS));

        int removed = outboxRepository.deletePublishedBefore(
                Instant.now().minus(30, ChronoUnit.DAYS));

        assertThat(removed).isEqualTo(1);
        assertThat(outboxRepository.findById(old)).isEmpty();
    }

    @Test
    @DisplayName("Keeps rows published inside the retention window")
    void keepsRecentPublishedRows() {
        // Saklamanin sebebi hata ayiklama: "bu olay gercekten gonderilmis
        // miydi" sorusu birkac gun sorulabilir.
        Long recent = row(Instant.now().minus(3, ChronoUnit.DAYS));

        outboxRepository.deletePublishedBefore(Instant.now().minus(30, ChronoUnit.DAYS));

        assertThat(outboxRepository.findById(recent)).isPresent();
    }

    @Test
    @DisplayName("Never removes an event that has not been published yet")
    void neverRemovesUnpublishedRows() {
        // ASIL KORUMA BU. Yayinlanmamis satir, gonderilmeyi bekleyen bir
        // olaydir; published_at kontrolu unutulsaydi temizlik, henuz
        // gonderilmemis olaylari SESSIZCE silerdi -- ve outbox deseninin
        // butun amaci o olayin kaybolmamasiydi.
        OutboxEvent pending = new OutboxEvent(UUID.randomUUID(), "CREATED",
                "employee.created", "{}", null);
        outboxRepository.saveAndFlush(pending);

        // Kesim noktasi GELECEKTE: tarih olcutu tek basina bu satiri secerdi.
        int removed = outboxRepository.deletePublishedBefore(
                Instant.now().plus(365, ChronoUnit.DAYS));

        assertThat(removed).isZero();
        assertThat(outboxRepository.findById(pending.getId())).isPresent();
    }
}
