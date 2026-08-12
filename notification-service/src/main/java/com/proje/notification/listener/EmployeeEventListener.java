package com.proje.notification.listener;

import com.proje.notification.config.RabbitConfig;
import com.proje.notification.entity.ProcessedEvent;
import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.repository.ProcessedEventRepository;
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

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationMailService mailService;
    private final ManagerLookupService managerLookupService;

    public EmployeeEventListener(ProcessedEventRepository processedEventRepository,
                                 NotificationMailService mailService,
                                 ManagerLookupService managerLookupService) {
        this.processedEventRepository = processedEventRepository;
        this.mailService = mailService;
        this.managerLookupService = managerLookupService;
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
            if (!claim(event)) {
                log.debug("Duplicate event ignored: {}", event.eventId());
                return;
            }

            mailService.send(event, managerLookupService.managerEmail(event.employeeId()));

            log.info("Notification sent for event {} ({})", event.eventId(), event.eventType());
        } finally {
            // Iplik havuzdan geliyor; temizlenmezse kimlik SONRAKI mesaja
            // sizar ve iki ayri isin loglari birbirine karisir.
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * Olayi "bu benim" diye isaretler.
     *
     * saveAndFlush sart: duz save() yazmayi yalnizca KUYRUGA ALIR ve gercek
     * INSERT commit aninda, yani mail coktan gittikten sonra calisirdi. O
     * durumda birincil anahtar mukerrer SATIRI engelleyebilir ama mukerrer
     * MAILI engelleyemezdi.
     *
     * @return olay ilk kez bu tuketici tarafindan sahiplenildiyse true
     */
    private boolean claim(EmployeeEvent event) {
        // Hizli yol: mukerrer teslimlerin buyuk cogunlugu burada elenir ve
        // istisna maliyeti hic odenmez.
        if (processedEventRepository.existsById(event.eventId())) {
            return false;
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEvent(
                    event.eventId(), event.eventType().name(), event.employeeId()));
            return true;

        } catch (DataIntegrityViolationException e) {
            // Yaris: baska bir tuketici ayni olayi biz kontrol ettikten sonra
            // kaydetti. Mail HENUZ gonderilmedi, dogru davranis atlamaktir.
            // Transaction geri alinabilir; mesaj yeniden teslim edildiginde
            // hizli yol devreye girer ve sessizce atlanir.
            log.info("Event already claimed by another consumer: {}", event.eventId());
            return false;
        }
    }
}
