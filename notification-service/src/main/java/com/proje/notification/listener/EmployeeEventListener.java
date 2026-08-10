package com.proje.notification.listener;

import com.proje.notification.config.RabbitConfig;
import com.proje.notification.entity.ProcessedEvent;
import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.repository.ProcessedEventRepository;
import com.proje.notification.service.ManagerLookupService;
import com.proje.notification.service.NotificationMailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EmployeeEventListener {

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
    public void onEmployeeEvent(EmployeeEvent event) {
        // Hizli yol: olay daha once islendiyse hicbir sey yapilmaz.
        if (processedEventRepository.existsById(event.eventId())) {
            log.debug("Duplicate event ignored: {}", event.eventId());
            return;
        }

        // Kayit mailden ONCE yazilir: mail gonderimi patlarsa transaction geri
        // alinir, kayit silinir ve mesaj yeniden denenir. Ters sirada, gonderilmis
        // mail geri alinamadigi icin tekrar denemede ikinci mail giderdi.
        processedEventRepository.save(new ProcessedEvent(
                event.eventId(), event.eventType().name(), event.employeeId()));

        mailService.send(event, managerLookupService.managerEmail(event.employeeId()));

        log.info("Notification sent for event {} ({})", event.eventId(), event.eventType());
    }
}
