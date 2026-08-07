package com.proje.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Eureka Server uygulamasi.
 *
 * @SpringBootApplication  -> Bu sinifi bir Spring Boot uygulamasi yapar
 *                            (otomatik yapilandirma + bilesen taramasi).
 * @EnableEurekaServer     -> Bu uygulamayi bir "servis rehberi"ne cevirir.
 *                            Diger servisler buraya kayit olur ve birbirini
 *                            isimle bulur.
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
