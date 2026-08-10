package com.proje.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Notification Service uygulamasinin giris noktasi.
 *
 * @EnableFeignClients olmadan @FeignClient arayuzleri taranmaz ve
 * uygulama "bean bulunamadi" hatasiyla acilmaz.
 */
@SpringBootApplication
@EnableFeignClients
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
