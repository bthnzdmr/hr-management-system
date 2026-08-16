package com.proje.notification.listener;

import com.proje.notification.config.RabbitConfig;
import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.service.EventClaimService;
import com.proje.notification.service.ManagerLookupService;
import com.proje.notification.service.NotificationMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EmployeeEventListener {

    /** Uretici ile ayni baslik ve ayni MDC anahtari; ikisi de sozlesmenin parcasi. */
    static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(EmployeeEventListener.class);

    private final NotificationMailService mailService;
    private final ManagerLookupService managerLookupService;
    private final EventClaimService eventClaimService;

    public EmployeeEventListener(NotificationMailService mailService,
                                 ManagerLookupService managerLookupService,
                                 EventClaimService eventClaimService) {
        this.mailService = mailService;
        this.managerLookupService = managerLookupService;
        this.eventClaimService = eventClaimService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @Transactional
    public void onEmployeeEvent(EmployeeEvent event,
                                @Header(name = CORRELATION_HEADER, required = false)
                                String correlationId) {
        // Kimlik uretici tarafindan mesaj basligina konuyor. Yoksa eventId
        // kullanilir: o da uctan uca akan ve benzersiz olan tek deger --
        // "izlenemez" olmaktansa "farkli bir anahtarla izlenebilir" iyidir.
        MDC.put(MDC_KEY, correlationId != null ? correlationId : event.eventId().toString());
        try {
            if (!claimed(event)) {
                log.debug("Duplicate event ignored: {}", event.eventId());
                return;
            }

            try {
                mailService.send(event, managerLookupService.managerEmail(event.employeeId()));
            } catch (RuntimeException failure) {
                // Sahiplenme KENDI transaction'inda commit edildi; buradaki geri
                // alma ona dokunmaz. Telafi edilmezse yeniden teslim "zaten
                // islendi" der, mesaj ACK'lenir ve mail SESSIZCE kaybolur --
                // DLQ'ya bile dusmez. Olculdu.
                releaseQuietly(event);
                throw failure;
            }

            log.info("Notification sent for event {} ({})", event.eventId(), event.eventType());
        } finally {
            // Iplik havuzdan geliyor; temizlenmezse kimlik SONRAKI mesaja
            // sizar ve iki ayri isin loglari birbirine karisir.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Sahiplenmeyi dener ve yarisi kaybetmeyi NORMAL bir sonuc sayar.
     *
     * Istisna, EventClaimService'in transaction sinirinin DISINDA yakalanir.
     * Iceride yakalansaydi o transaction rollback-only isaretli kalir ve kendi
     * commit'inde UnexpectedRollbackException firlatirdi -- mesaj reddedilir,
     * bir retry hakki yanardi. Olculdu.
     */
    /** Telafi basarisiz olsa bile OZGUN hata yukari cikmali; yoksa sebep kaybolur. */
    private void releaseQuietly(EmployeeEvent event) {
        try {
            eventClaimService.release(event.eventId());
        } catch (RuntimeException ex) {
            log.error("Could not release the claim on event {}; it will not be retried",
                    event.eventId(), ex);
        }
    }

    private boolean claimed(EmployeeEvent event) {
        try {
            return eventClaimService.claim(event);
        } catch (DataIntegrityViolationException e) {
            // Yaris: baska bir tuketici ayni olayi biz kontrol ettikten sonra
            // kaydetti. Mail HENUZ gonderilmedi, dogru davranis atlamaktir.
            log.info("Event already claimed by another consumer: {}", event.eventId());
            return false;
        }
    }

}
