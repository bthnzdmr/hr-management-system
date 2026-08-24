package com.proje.notification.listener;

import com.proje.notification.config.RabbitConfig;
import com.proje.notification.event.LeaveEvent;
import com.proje.notification.service.EventClaimService;
import com.proje.notification.service.LeaveMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Izin olaylarini isler. Diger iki dinleyiciyle ayni desen: sahiplen, gonder,
 * basarisizsa sahiplenmeyi GERI AL.
 */
@Component
public class LeaveEventListener {

    static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(LeaveEventListener.class);

    private final LeaveMailService mailService;
    private final EventClaimService eventClaimService;

    public LeaveEventListener(LeaveMailService mailService, EventClaimService eventClaimService) {
        this.mailService = mailService;
        this.eventClaimService = eventClaimService;
    }

    @RabbitListener(queues = RabbitConfig.LEAVE_QUEUE)
    @Transactional
    public void onLeaveEvent(LeaveEvent event,
                             @Header(name = CORRELATION_HEADER, required = false)
                             String correlationId) {

        // Baslik yoksa yedek deger `eventId`: "izlenemez" olmaktansa farkli bir
        // anahtarla izlenebilir olmak iyidir.
        MDC.put(MDC_KEY, correlationId != null ? correlationId : event.eventId().toString());
        try {
            if (!claimed(event)) {
                log.debug("Duplicate event ignored: {}", event.eventId());
                return;
            }

            try {
                mailService.send(event);
            } catch (RuntimeException failure) {
                // Sahiplenme KENDI transaction'inda commit edildi. Telafi
                // edilmezse yeniden teslim "zaten islendi" der ve bildirim
                // SESSIZCE kaybolur -- DLQ'ya bile dusmez. Bu tam olarak bir
                // kez olculdu ve kapatildi.
                releaseQuietly(event);
                throw failure;
            }

            log.info("Leave notification handled for event {} ({})",
                    event.eventId(), event.eventType());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private void releaseQuietly(LeaveEvent event) {
        try {
            eventClaimService.release(event.eventId());
        } catch (RuntimeException ex) {
            log.error("Could not release the claim on event {}; it will not be retried",
                    event.eventId(), ex);
        }
    }

    /** Istisna, EventClaimService'in transaction sinirinin DISINDA yakalanir. */
    private boolean claimed(LeaveEvent event) {
        try {
            return eventClaimService.claim(
                    event.eventId(), event.eventType().name(), event.leaveRequestId());
        } catch (DataIntegrityViolationException e) {
            log.info("Event already claimed by another consumer: {}", event.eventId());
            return false;
        }
    }
}
