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
 * <p>Tablo sinirsiz buyuyordu: her olay kalici bir satir birakiyor ve hicbiri
 * silinmiyordu. Kardes tablolar icin (outbox, refresh_token) temizlik zaten
 * vardi; burada yoktu ve ayni gerekce birebir gecerli -- yigin, yedek ve
 * VACUUM maliyeti dogrusal olarak buyur.
 *
 * <p><b>Saklama suresi bir idempotency penceresidir.</b> Bir mesajin broker'da
 * bekleyebilecegi en uzun sureden guvenle uzun olmalidir: satir erken
 * silinirse gec teslim edilen bir olay "hic islenmemis" gorunur ve ayni
 * personele ikinci bir mail gider. Bu yuzden varsayilan comert.
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

    /**
     * initialDelay SART: acilir acilmaz silme calistirmak acilis anini
     * yavaslatir ve ilk mesajlarla veritabani icin yarisir.
     */
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
