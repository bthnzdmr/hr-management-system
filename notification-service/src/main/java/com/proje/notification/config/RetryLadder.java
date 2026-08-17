package com.proje.notification.config;

import java.time.Duration;
import java.util.List;

/**
 * Gecikmeli yeniden deneme merdiveni.
 *
 * NEDEN GEREKLI: sureç ici retry SANIYELER olceginde calisir. Olculen gercek
 * ariza ise saatler olcegindeydi -- bir sema uyumsuzlugu yuzunden 16 mesaj bes
 * denemesini saniyeler icinde tuketip DLQ'ya dustu, oysa dogru imaj
 * dagitildiginda ariza kendiliginden gecti. Merdiven olsaydi o mesajlar hic
 * insan mudahalesi olmadan teslim edilirdi.
 *
 * KALIP: RabbitMQ'da kanonik cozum TTL + DLX zinciridir. Mesaj basamaga
 * konur, TTL dolunca kendiliginden tuketici kuyruguna geri duser. Boylece
 * ustel geri cekilme SAATLER olceginde olur.
 *
 * DLQ bir kuyruk degil bir PARK YERIDIR: merdivenin sonuna gelen mesaj orada
 * kalir ve uyari atesler. "Vazgecmeyi bilmeyen yeniden deneme", kuyrugun onunu
 * tikayan seyin ta kendisidir.
 */
final class RetryLadder {

    /**
     * Basamaklar.
     *
     * Sureler SABIT ve adlandirmayla tutarli: yonetim panelinde kuyrugun adi
     * ne kadar bekledigini soylemeli. Yapilandirilabilir yapilsaydi ad ile
     * gercek sure birbirinden ayrilabilir ve panel yalan soylerdi.
     */
    static final List<Rung> RUNGS = List.of(
            new Rung("5m", Duration.ofMinutes(5)),
            new Rung("30m", Duration.ofMinutes(30)),
            new Rung("2h", Duration.ofHours(2)));

    /** Mesajin kacinci basamakta oldugunu tasiyan baslik. */
    static final String ATTEMPT_HEADER = "x-retry-rung";

    private RetryLadder() {
    }

    /** Bir basamagin kuyruk adi: `employee.notification.queue.retry.5m` */
    static String queueName(String consumerQueue, Rung rung) {
        return consumerQueue + ".retry." + rung.suffix();
    }

    /** Tuketici kuyruguna karsilik gelen park kuyrugu. */
    static String deadLetterQueueName(String consumerQueue) {
        // "employee.notification.queue" -> "employee.notification.dlq"
        return consumerQueue.replaceAll("\\.queue$", ".dlq");
    }

    record Rung(String suffix, Duration ttl) {
    }
}
