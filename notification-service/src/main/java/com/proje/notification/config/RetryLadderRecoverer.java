package com.proje.notification.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

/**
 * Surec ici retry bittiginde mesaji BIR SONRAKI basamaga tasir; merdivenin
 * sonunda park kuyruguna birakir.
 *
 * Onceki hali `RepublishMessageRecoverer(rabbitTemplate, DLX, DLQ)` idi ve iki
 * kusuru vardi:
 *
 * 1. GECIKME YOKTU. Mesaj bes denemesini saniyeler icinde tuketip dogrudan
 *    parka dusuyordu. Olculen ariza ise saatler olcegindeydi.
 *
 * 2. HEDEF SABITTI. `DLQ` sabiti "employee.notification.dlq"; recoverer ise
 *    HER kuyruk icin calisiyor. Yani bir HESAP olayi retry hakkini
 *    tukettiginde personel park kuyruguna dusuyordu. Kuyruklardaki
 *    `deadLetterRoutingKey` yalnizca red/nack yolunda gecerli -- recoverer o
 *    yolu atlayip dogrudan yayinliyor. Hedef artik mesajin GELDIGI kuyruktan
 *    turetiliyor.
 */
class RetryLadderRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(RetryLadderRecoverer.class);

    /**
     * Baslik adlari `RepublishMessageRecoverer` ile AYNI tutuldu.
     *
     * DLQ'ya bakan kisi ve daha once yazilmis oynatma yordami bu adlari
     * biliyor; degistirmek, calisan bir usulu sessizce bozmak olurdu.
     */
    private static final String EXCEPTION_MESSAGE = "x-exception-message";
    private static final String ORIGINAL_EXCHANGE = "x-original-exchange";
    private static final String ORIGINAL_ROUTING_KEY = "x-original-routingKey";

    private final RabbitTemplate rabbitTemplate;
    private final String retryExchange;
    private final String deadLetterExchange;

    RetryLadderRecoverer(RabbitTemplate rabbitTemplate, String retryExchange,
                         String deadLetterExchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.retryExchange = retryExchange;
        this.deadLetterExchange = deadLetterExchange;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        MessageProperties properties = message.getMessageProperties();
        String sourceQueue = properties.getConsumerQueue();

        if (sourceQueue == null) {
            // Kaynak kuyruk bilinmiyorsa hedef de turetilemez. Sessizce
            // atmaktansa gorunur bir hata birakilir.
            throw new IllegalStateException(
                    "Cannot recover a message without a source queue", cause);
        }

        int rung = currentRung(properties);

        stampOrigin(properties, message);
        properties.setHeader(EXCEPTION_MESSAGE, describe(cause));

        if (rung < RetryLadder.RUNGS.size()) {
            RetryLadder.Rung next = RetryLadder.RUNGS.get(rung);
            properties.setHeader(RetryLadder.ATTEMPT_HEADER, rung + 1);

            String target = RetryLadder.queueName(sourceQueue, next);
            rabbitTemplate.send(retryExchange, target, message);

            log.warn("Delivery failed, retrying in {}: queue={} rung={}/{} reason={}",
                    next.suffix(), sourceQueue, rung + 1, RetryLadder.RUNGS.size(),
                    describe(cause));
            return;
        }

        // Merdiven bitti: mesaj PARK edilir ve uyari atesler. Buradan geri
        // cekmek kasitli bir insan kararidir -- retry hakki coktan tukendi ve
        // sebebini bilmeden yeniden enjekte etmek ayni arizayi tekrar uretir.
        String park = RetryLadder.deadLetterQueueName(sourceQueue);
        rabbitTemplate.send(deadLetterExchange, park, message);

        log.error("Retry ladder exhausted, parking message: queue={} target={} reason={}",
                sourceQueue, park, describe(cause));
    }

    private int currentRung(MessageProperties properties) {
        Object header = properties.getHeader(RetryLadder.ATTEMPT_HEADER);

        return header instanceof Number number ? number.intValue() : 0;
    }

    /**
     * Ozgun exchange ve routing key YALNIZCA ilk seferde yazilir.
     *
     * Her basamakta yeniden yazilsaydi ikinci turda "ozgun" degerler
     * merdivenin kendi adresleri olurdu ve mesajin nereden geldigi kaybolurdu
     * -- oynatma yordami tam da bu basligi kullaniyor.
     */
    private void stampOrigin(MessageProperties properties, Message message) {
        if (properties.getHeader(ORIGINAL_ROUTING_KEY) != null) {
            return;
        }

        properties.setHeader(ORIGINAL_EXCHANGE, message.getMessageProperties().getReceivedExchange());
        properties.setHeader(ORIGINAL_ROUTING_KEY,
                message.getMessageProperties().getReceivedRoutingKey());
    }

    /** Yigin izi DEGIL, tek satir: baslik uzunlugu sinirlidir ve okunmalidir. */
    private String describe(Throwable cause) {
        Throwable root = cause;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }
}
