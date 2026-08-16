package com.proje.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification Service uygulamasinin giris noktasi.
 *
 * @EnableFeignClients olmadan @FeignClient arayuzleri taranmaz ve
 * uygulama "bean bulunamadi" hatasiyla acilmaz.
 *
 * @EnableScheduling de ayni sinifta: bu servis simdiye kadar hic zamanlanmis
 * is calistirmiyordu ve isaret olmadan @Scheduled metotlari SESSIZCE hic
 * calismaz -- uygulama sorunsuz acilir, is yalnizca yapilmaz.
 */
@SpringBootApplication
@EnableFeignClients
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
