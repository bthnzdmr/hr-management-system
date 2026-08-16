package com.proje.notification.service;

import com.proje.notification.entity.ProcessedEvent;
import com.proje.notification.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Idempotency penceresi: eski kayit silinir, yeni kayit KORUNUR. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
class ProcessedEventCleanupTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID old;
    private UUID recent;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAllInBatch();

        old = write(Instant.now().minus(40, ChronoUnit.DAYS));
        recent = write(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private UUID write(Instant processedAt) {
        ProcessedEvent event = new ProcessedEvent(UUID.randomUUID(), "CREATED", 42L);
        processedEventRepository.saveAndFlush(event);

        // processed_at veritabani tarafindan atanir; gecmise cekmek icin
        // uretim arayuzune test-ozel bir metot eklemek yerine dogrudan yazilir.
        entityManager.createNativeQuery(
                        "UPDATE processed_event SET processed_at = :at WHERE event_id = :id")
                .setParameter("at", processedAt)
                .setParameter("id", event.getEventId())
                .executeUpdate();
        entityManager.clear();

        return event.getEventId();
    }

    @Test
    @DisplayName("Deletes records past the retention window and keeps the rest")
    void deletesOnlyOldRecords() {
        int removed = processedEventRepository.deleteProcessedBefore(
                Instant.now().minus(30, ChronoUnit.DAYS));

        assertThat(removed).isEqualTo(1);
        assertThat(processedEventRepository.findById(old)).isEmpty();
        // Pencere icindeki kayit KALMALI: erken silinirse gec teslim edilen bir
        // olay "hic islenmemis" gorunur ve ikinci bir mail gider.
        assertThat(processedEventRepository.findById(recent)).isPresent();
    }
}
