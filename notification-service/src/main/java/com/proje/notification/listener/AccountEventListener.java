package com.proje.notification.listener;

import com.proje.notification.config.RabbitConfig;
import com.proje.notification.event.AccountEvent;
import com.proje.notification.service.EventClaimService;
import com.proje.notification.service.PasswordResetMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hesap olaylarini isler. EmployeeEventListener ile ayni desen: sahiplen,
 * gonder, basarisizsa sahiplenmeyi geri al.
 */
@Component
public class AccountEventListener {

    static final String CORRELATION_HEADER = "X-Correlation-Id";
    private static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(AccountEventListener.class);

    private final PasswordResetMailService mailService;
    private final EventClaimService eventClaimService;

    public AccountEventListener(PasswordResetMailService mailService,
                                EventClaimService eventClaimService) {
        this.mailService = mailService;
        this.eventClaimService = eventClaimService;
    }

    @RabbitListener(queues = RabbitConfig.ACCOUNT_QUEUE)
    @Transactional
    public void onAccountEvent(AccountEvent event,
                               @Header(name = CORRELATION_HEADER, required = false)
                               String correlationId) {

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
                // edilmezse yeniden teslim "zaten islendi" der ve sifirlama
                // maili SESSIZCE kaybolur -- kullanici hicbir zaman
                // baglantiyi alamaz.
                releaseQuietly(event);
                throw failure;
            }

            log.info("Password reset mail sent for event {}", event.eventId());
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private void releaseQuietly(AccountEvent event) {
        try {
            eventClaimService.release(event.eventId());
        } catch (RuntimeException ex) {
            log.error("Could not release the claim on event {}; it will not be retried",
                    event.eventId(), ex);
        }
    }

    /** Istisna, EventClaimService'in transaction sinirinin DISINDA yakalanir. */
    private boolean claimed(AccountEvent event) {
        try {
            return eventClaimService.claim(
                    event.eventId(), event.eventType().name(), event.userId());
        } catch (DataIntegrityViolationException e) {
            log.info("Event already claimed by another consumer: {}", event.eventId());
            return false;
        }
    }
}
