package com.proje.employee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Employee Service uygulamasinin giris noktasi.
 *
 * Component taramasi bu paketten asagi dogru calisir; sinif daha derin bir
 * pakete konursa controller ve service siniflari bulunamaz.
 *
 * Eureka icin ayrica anotasyon yoktur: istemci bagimliligi classpath'te
 * oldugu icin otomatik yapilandirma devreye girer.
 *
 * @EnableScheduling outbox relay'in periyodik calismasi icindir; olmadan
 * @Scheduled anotasyonlari sessizce yok sayilir.
 */
@SpringBootApplication
@EnableScheduling
public class EmployeeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeServiceApplication.class, args);
    }
}
