package com.proje.employee.event;

import com.proje.employee.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Yayinlanmis outbox satirlarini saklama suresi sonunda siler.
 *
 * Her personel islemi bir satir birakiyordu ve hicbiri silinmiyordu. Kismi
 * index relay'in SORGUSUNU hizli tutar ama tabloyu kucultmez: yigin, yedek ve
 * VACUUM maliyeti dogrusal ve kalici olarak buyur.
 *
 * Ayri bir sinif, RefreshTokenCleanup ile ayni gerekce: OutboxRelay yayin
 * akisindan sorumlu, burasi bakim isinden. Ayni sinifta olsalardi relay
 * testlerinin her kosusu bir temizlik zamanlayicisini da devreye sokardi.
 */
@Component
public class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);

    private final OutboxRepository outboxRepository;
    private final Duration retention;

    public OutboxCleanup(OutboxRepository outboxRepository,
                         @Value("${app.outbox.retention-days}") int retentionDays) {
        this.outboxRepository = outboxRepository;
        this.retention = Duration.ofDays(retentionDays);
    }

    /**
     * initialDelay SART: uygulama acilir acilmaz silme calistirmak, acilis
     * anini gereksiz yere yavaslatir ve ilk isteklerle veritabani icin yarisir.
     */
    @Scheduled(
            fixedDelayString = "${app.outbox.cleanup-interval-ms}",
            initialDelayString = "${app.outbox.cleanup-interval-ms}")
    @Transactional
    public void deleteOldPublishedEvents() {
        int removed = outboxRepository.deletePublishedBefore(Instant.now().minus(retention));

        // Sifir silinen her turda loglanirsa log gurultuye doner.
        if (removed > 0) {
            log.info("Removed {} published outbox rows older than {} days",
                    removed, retention.toDays());
        }
    }
}
