package com.proje.personel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Personel Service uygulamasinin giris noktasi.
 *
 * @SpringBootApplication uc anotasyonun kisayoludur:
 *   - @Configuration           : bu sinif bean tanimlayabilir
 *   - @EnableAutoConfiguration : classpath'te ne varsa ona gore otomatik yapilandirir
 *                                (Tomcat gorur -> web sunucusu baslatir,
 *                                 PostgreSQL surucusu gorur -> DataSource kurar)
 *   - @ComponentScan           : BU paketi ve ALT paketlerini tarar
 *
 * Sinifin en ust pakette (com.proje.personel) durmasi zorunlu degil ama gereklidir:
 * component taramasi buradan asagi dogru calisir. Daha derin bir pakete konursa
 * controller ve service siniflari bulunamaz.
 *
 * Eureka icin ayrica bir anotasyon YOKTUR. Istemci bagimliligi classpath'te
 * oldugu icin otomatik yapilandirma devreye girer ve servis kendini kaydeder.
 */
@SpringBootApplication
public class PersonelServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PersonelServiceApplication.class, args);
    }
}
