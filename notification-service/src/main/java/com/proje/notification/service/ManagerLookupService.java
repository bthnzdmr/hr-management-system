package com.proje.notification.service;

import com.proje.notification.client.EmployeeClient;
import com.proje.notification.client.ServiceTokenProvider;
import com.proje.notification.client.dto.EmployeeSummary;
import feign.FeignException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Personelin yoneticisinin e-posta adresini bulur.
 *
 * Bu bir ZENGINLESTIRMEDIR, mail gondermenin on kosulu degildir: cagri
 * basarisiz olursa bos deger doner ve mail CC'siz gider. Aksi halde Employee
 * Service'in gecici bir arizasi bildirimi tamamen engellerdi -- kuyrugu tam da
 * bu bagi koparmak icin secmistik.
 */
@Service
public class ManagerLookupService {

    private static final Logger log = LoggerFactory.getLogger(ManagerLookupService.class);

    private final EmployeeClient employeeClient;
    private final ServiceTokenProvider tokenProvider;

    /**
     * Uc SONUC ayri ayri sayilir.
     *
     * Yalnizca bir "hata" sayaci yeterli olmazdi: sifir hata, "her sey yolunda"
     * ile "hic cagri yapilmadi" arasinda AYRIM YAPMAZ. Ucu birden sayilinca
     * oran sorulabiliyor.
     *
     * Ayrim bugun olculen arizanin ta kendisi: CC'nin bos olmasinin iki ayri
     * sebebi var ve disaridan ikisi de AYNI gorunuyor -- kisinin gercekten
     * yoneticisi yok (`no_manager`), ya da cagri kirildi (`failed`). Log'da
     * bir WARN vardi ama kimse bakmiyordu.
     *
     * Etiket kumesi KAPALI (uc deger). Istisna sinifiyla etiketlemek cazipti
     * ama her yeni hata turu yeni bir zaman serisi acardi -- kardinalite
     * patlamasi, metrik yiginlarinin klasik hatasi.
     */
    private final Counter found;
    private final Counter noManager;
    private final Counter failed;

    public ManagerLookupService(EmployeeClient employeeClient, ServiceTokenProvider tokenProvider,
                                MeterRegistry registry) {
        this.employeeClient = employeeClient;
        this.tokenProvider = tokenProvider;

        this.found = counter(registry, "found");
        this.noManager = counter(registry, "no_manager");
        this.failed = counter(registry, "failed");
    }

    /**
     * Sayaclar acilista OLUSTURULUR, ilk olayda degil.
     *
     * Micrometer bir sayaci ancak dokunuldugunda yayinlar; kayit edilmemis bir
     * seri Prometheus'ta HIC gorunmez ve kural sessizce hicbir zaman
     * atesLENMEZ. Ayni tuzak bu projede DLQ gostergesinde ve gecikme panelinde
     * iki kez yasandi: olculuyor gorunen ama hic olculmeyen bir gosterge, hic
     * olmayandan kotudur.
     */
    private static Counter counter(MeterRegistry registry, String outcome) {
        return Counter.builder("hr.manager.lookup")
                .description("Manager lookups made while enriching a notification mail")
                .tag("outcome", outcome)
                .register(registry);
    }

    public Optional<String> managerEmail(Long employeeId) {
        try {
            return record(lookup(employeeId));

        } catch (FeignException.Unauthorized e) {
            // Onbellekteki token sunucu tarafinda gecersiz (ornegin imzalama
            // anahtari degismis). Atilir ve bir kez yeniden denenir.
            log.info("Service token rejected, renewing and retrying once");
            tokenProvider.invalidate();
            return lookupQuietly(employeeId);

        } catch (Exception e) {
            failed.increment();
            log.warn("Manager lookup failed for employee {}, mail will be sent without CC: {}",
                    employeeId, e.toString());
            return Optional.empty();
        }
    }

    /** Basarili bir cagrinin iki ayri sonucu vardir ve ayri sayilir. */
    private Optional<String> record(Optional<String> email) {
        if (email.isPresent()) {
            found.increment();
        } else {
            noManager.increment();
        }

        return email;
    }

    private Optional<String> lookup(Long employeeId) {
        EmployeeSummary employee = employeeClient.findById(employeeId);

        if (employee.managerId() == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(employeeClient.findById(employee.managerId()).email());
    }

    private Optional<String> lookupQuietly(Long employeeId) {
        try {
            return record(lookup(employeeId));
        } catch (Exception e) {
            failed.increment();
            log.warn("Manager lookup failed after token renewal for employee {}: {}",
                    employeeId, e.toString());
            return Optional.empty();
        }
    }
}
