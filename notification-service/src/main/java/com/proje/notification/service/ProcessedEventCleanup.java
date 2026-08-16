package com.proje.notification.service;

import com.proje.notification.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Idempotency kayitlarinin temizligi.
 *
 * <p>Saklama suresi bir idempotency penceresidir: erken silinen satir, gec
 * teslim edilen olayi "hic islenmemis" gosterir ve ikinci bir mail gider.
 */
@Component
public class ProcessedEventCleanup {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanup.class);

    private final ProcessedEventRepository processedEventRepository;
    private final Duration retention;

    public ProcessedEventCleanup(ProcessedEventRepository processedEventRepository,
                                 @Value("${app.processed-event.retention-days}") int retentionDays) {
        this.processedEventRepository = processedEventRepository;
        this.retention = Duration.ofDays(retentionDays);
    }

    /** initialDelay: acilista silme calistirmak ilk mesajlarla yarisirdi. */
    @Scheduled(
            fixedDelayString = "${app.processed-event.cleanup-interval-ms}",
            initialDelayString = "${app.processed-event.cleanup-interval-ms}")
    @Transactional
    public void deleteOldRecords() {
        int removed = processedEventRepository.deleteProcessedBefore(Instant.now().minus(retention));

        if (removed > 0) {
            log.info("Removed {} processed-event records older than {} days",
                    removed, retention.toDays());
        }
    }
}
