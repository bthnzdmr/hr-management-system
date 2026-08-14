package com.proje.employee.event;

import com.proje.employee.config.RabbitConfig;
import com.proje.employee.config.CorrelationIdFilter;
import com.proje.employee.entity.OutboxEvent;
import com.proje.employee.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final int maxAttempts;
    private final int batchSize;
    private final long confirmTimeoutMs;

    public OutboxRelay(OutboxRepository outboxRepository,
                       RabbitTemplate rabbitTemplate,
                       @Value("${app.outbox.max-attempts}") int maxAttempts,
                       @Value("${app.outbox.batch-size}") int batchSize,
                       @Value("${app.outbox.confirm-timeout-ms}") long confirmTimeoutMs) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.confirmTimeoutMs = confirmTimeoutMs;
    }

    // Okuma, gonderme ve isaretleme tek transaction icinde olmak zorunda:
    // FOR UPDATE ile alinan kilit transaction bitince birakilir.
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxRepository.lockPending(maxAttempts, batchSize);

        for (OutboxEvent event : pending) {
            // Zamanlayici ipliginin MDC'si bos: her satir icin kendi kimligi
            // konur, aksi halde relay'in loglari izlenemez kalirdi.
            // finally sart -- iplik havuzdan geldigi icin kimlik sonraki
            // satira sizardi.
            if (event.getCorrelationId() != null) {
                MDC.put(CorrelationIdFilter.MDC_KEY, event.getCorrelationId());
            }
            try {
                if (!tryPublish(event)) {
                    // Altyapi erisilemez durumda: kalan satirlari denemek bosuna.
                    break;
                }
            } finally {
                MDC.remove(CorrelationIdFilter.MDC_KEY);
            }
        }
    }

    /**
     * @return altyapi calisir durumdaysa true (mesaj gitmis ya da mesajin kendi
     *         kusuruyla reddedilmis olabilir), broker'a hic ulasilamiyorsa false
     */
    private boolean tryPublish(OutboxEvent event) {
        CorrelationData correlation = new CorrelationData(event.getEventId().toString());
        try {
            rabbitTemplate.send(RabbitConfig.EXCHANGE, event.getRoutingKey(), toMessage(event), correlation);

            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);

            if (confirm.isAck()) {
                event.markPublished();
                log.debug("Outbox event published: {} {}", event.getEventType(), event.getEventId());
            } else if (isInfrastructureFailure(confirm.getReason())) {
                // Kanal send()'den SONRA oldu. Kural zaten yaziliydi --
                // "erisilemezlik o mesajin kusuru degildir" -- ama yalnizca
                // send()'in firlattigi istisnalar icin isliyordu. Burada nack
                // olarak geliyor ve sayaci HAKSIZ YERE artiriyordu: tek bir
                // broker yeniden baslatmasi, ucustaki 20 mesajin bes hakkindan
                // birini birden yakardi.
                log.warn("Confirm failed for infrastructure reasons, not counted: {} ({})",
                        event.getEventId(), confirm.getReason());
                return false;

            } else {
                // Broker mesaji acikca reddetti: bu mesajin kendi kusuru.
                recordFailure(event, "nack: " + confirm.getReason());
            }
            return true;

        } catch (AmqpConnectException | AmqpIOException e) {
            // Broker'a ulasilamiyor. Bu mesajin kusuru degil, bu yuzden deneme
            // sayaci artirilmaz; aksi halde tek bir kesinti bekleyen tum
            // olaylarin hakkini birden tuketirdi.
            log.warn("Broker unreachable, outbox delivery postponed: {}", e.getMessage());
            return false;

        } catch (TimeoutException e) {
            // Onay gelmedi: mesajin gidip gitmedigi bilinmiyor. Yayinlanmis
            // isaretlenmez; tekrar gonderilir ve tuketici eventId ile ayikla.
            log.warn("Confirm timed out, outbox delivery postponed: {}", event.getEventId());
            return false;

        } catch (ExecutionException e) {
            // Onay beklenirken kanal oldu: yine altyapi kaynakli olabilir.
            if (isInfrastructureFailure(String.valueOf(e.getCause()))) {
                log.warn("Confirm failed while the channel was closing: {}", event.getEventId());
                return false;
            }
            recordFailure(event, String.valueOf(e.getCause()));
            return true;

        } catch (InterruptedException e) {
            // Kesinti genelde kapanista gelir; iz birakmadan gecilirse
            // "bu parti neden yarida kaldi" sorusu cevapsiz kalir.
            log.warn("Outbox publishing interrupted at {}", event.getEventId());
            Thread.currentThread().interrupt();
            return false;

        } catch (AmqpException e) {
            recordFailure(event, e.getMessage());
            return true;
        }
    }

    private void recordFailure(OutboxEvent event, String reason) {
        event.markFailed(reason);
        if (event.getAttempts() >= maxAttempts) {
            log.error("Outbox event abandoned after {} attempts: {} ({})",
                    event.getAttempts(), event.getEventId(), reason);
        } else {
            log.warn("Outbox event rejected, attempt {}: {} ({})",
                    event.getAttempts(), event.getEventId(), reason);
        }
    }

    /**
     * Reddin sebebi ALTYAPI mi, mesajin kendisi mi?
     *
     * Ayrim onemli cunku "attempts" sayaci yalnizca mesajin KENDI kusurunu
     * saymalidir. Broker yeniden baslarken bekleyen onaylar sebepsiz veya
     * "channel closed" gibi baglanti kaynakli sebeplerle tamamlanir; bunlari
     * saymak, bes yeniden baslatmadan sonra saglam olaylari kalici olarak
     * terk etmek demektir.
     *
     * Sebep NULL ise altyapi sayilir: broker bir mesaji kendi kusuru yuzunden
     * reddettiginde sebep bildirir.
     */
    private boolean isInfrastructureFailure(String reason) {
        if (reason == null || reason.isBlank()) {
            return true;
        }
        String lower = reason.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("channel") || lower.contains("connection")
                || lower.contains("shutdown") || lower.contains("closed");
    }

    // payload zaten JSON metnidir. Donusturucuye verilirse ikinci kez kodlanir;
    // bu yuzden ham bayt olarak gonderilir.
    private Message toMessage(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setContentEncoding(StandardCharsets.UTF_8.name());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setMessageId(event.getEventId().toString());

        // Kimlik mesajla birlikte kuyrugu gecer: tuketici bunu okuyup kendi
        // MDC'sine koyar ve iki servisin loglari ayni kimlikle birlesir.
        // Migration oncesi satirlarda null olabilir; bos baslik gonderilmez.
        if (event.getCorrelationId() != null) {
            properties.setHeader(CorrelationIdFilter.HEADER, event.getCorrelationId());
        }

        return new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties);
    }
}
