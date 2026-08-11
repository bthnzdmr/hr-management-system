package com.proje.notification.repository;

import com.proje.notification.entity.ProcessedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Idempotency garantisinin VERITABANI tarafini dogrular.
 *
 * Mock'lu testler bunu gosteremez: buradaki soru, Spring Data'nin gercekten
 * INSERT uretip uretmedigidir. ProcessedEvent Persistable uygulamasaydi
 * atanmis id yuzunden merge() calisir, ikinci kayit sessizce UPDATE'e
 * donusur ve birincil anahtar hic devreye girmezdi.
 *
 * Calisan bir PostgreSQL gerektirir: docker compose up -d
 */
@DataJpaTest
// Gomulu veritabaniyla degistirilmez: kisitin PostgreSQL'de gercekten
// calistigini dogruluyoruz, taklit bir veritabaninda degil.
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProcessedEventRepositoryTest {

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @BeforeEach
    void setUp() {
        // Onceki calismalardan kalan veriye bagimli olmamak icin.
        processedEventRepository.deleteAllInBatch();
    }

    private ProcessedEvent event(UUID eventId) {
        return new ProcessedEvent(eventId, "CREATED", 42L);
    }

    @Test
    @DisplayName("Rejects a second record carrying the same event id")
    void rejectsDuplicateEventId() {
        UUID eventId = UUID.randomUUID();
        processedEventRepository.saveAndFlush(event(eventId));

        assertThatThrownBy(() -> processedEventRepository.saveAndFlush(event(eventId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Stores the event and stamps the moment it was processed")
    void storesEventWithTimestamp() {
        UUID eventId = UUID.randomUUID();

        ProcessedEvent saved = processedEventRepository.saveAndFlush(event(eventId));

        assertThat(saved.getProcessedAt()).isNotNull();
        assertThat(processedEventRepository.existsById(eventId)).isTrue();
    }

    @Test
    @DisplayName("Reports an unseen event id as not processed")
    void reportsUnseenEventAsNotProcessed() {
        assertThat(processedEventRepository.existsById(UUID.randomUUID())).isFalse();
    }
}
