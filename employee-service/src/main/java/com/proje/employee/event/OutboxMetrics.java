package com.proje.employee.event;

import com.proje.employee.repository.OutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Outbox'in iki olculebilir hali.
 *
 * Tasarim notlari "yayin gecikmesi olculebilir" diyordu ve sema bunu BILEREK
 * mumkun kilmisti (boolean yerine zaman damgasi). Ama hicbir yerde
 * OLCULMUYORDU: olculebilir olmak ile olculuyor olmak farkli seylerdir.
 *
 * Gauge secildi, counter degil: ikisi de "su anki durum" sorusunu cevapliyor.
 * Counter yalnizca artan bir toplam tutar ve "kac olay bekliyor" sorusuna
 * cevap veremezdi.
 *
 * Deger her kazimada okunur (15 sn). Sorgu kismi index uzerinden calisir ve
 * yayinlanmis satirlar o index'te durmaz.
 */
@Component
public class OutboxMetrics implements MeterBinder {

    private final OutboxRepository outboxRepository;

    public OutboxMetrics(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        registry.gauge("hr.outbox.pending", this,
                metrics -> metrics.outboxRepository.countPending());

        registry.gauge("hr.outbox.publish.lag.seconds", this, OutboxMetrics::oldestPendingAgeSeconds);
    }

    /**
     * En eski bekleyen olayin yasi. Bekleyen yoksa SIFIR.
     *
     * NaN donmek daha "dogru" gorunebilir ama grafik o noktada kirilir ve
     * "birikim yok" ile "olcum yok" ayirt edilemez hale gelir.
     */
    private static double oldestPendingAgeSeconds(OutboxMetrics metrics) {
        Instant oldest = metrics.outboxRepository.oldestPendingCreatedAt();
        if (oldest == null) {
            return 0;
        }
        return Duration.between(oldest, Instant.now()).toMillis() / 1000.0;
    }
}
