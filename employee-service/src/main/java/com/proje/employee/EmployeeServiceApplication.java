package com.proje.employee;

import org.springframework.boot.SpringApplication;
import com.proje.employee.config.LeavePolicy;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
 * @EnableConfigurationProperties acikca yaziliyor, tarama DEGIL: hangi
 * yapilandirma sinifinin bagli oldugu kod okunarak gorulebilmeli.
 *
 * @EnableScheduling outbox relay'in periyodik calismasi icindir; olmadan
 * @Scheduled anotasyonlari sessizce yok sayilir.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(LeavePolicy.class)
public class EmployeeServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmployeeServiceApplication.class, args);
    }
}
