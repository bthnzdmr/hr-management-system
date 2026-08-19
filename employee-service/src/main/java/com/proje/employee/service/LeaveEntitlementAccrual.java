package com.proje.employee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Icinde bulunulan yilin eksik hak satirlarini tamamlar.
 *
 * NEDEN GUNLUK, YILDA BIR DEGIL?
 *
 * "1 Ocak'ta calis" diyen bir cron uc sorunu birden tasirdi: servis o gece
 * kapaliysa is O YIL HIC calismaz; yil ortasinda ise alinan personel ertesi
 * yila kadar haksiz kalir; ve gelistirme sirasinda calistigi hicbir zaman
 * GORULEMEZ.
 *
 * Gunluk ve idempotent bir is ucunu birden cozer. Bedeli gunde bir sorgu:
 * kadro bir kez tarandiktan sonra liste bos doner ve hicbir sey yazilmaz.
 */
@Component
public class LeaveEntitlementAccrual {

    private static final Logger log = LoggerFactory.getLogger(LeaveEntitlementAccrual.class);

    private final LeaveEntitlementService entitlements;
    private final Clock clock;

    // @Autowired SART: iki kurucu var ve Spring hangisini kullanacagini bilemez.
    // Isaretlenmezse baglam ACILMAZ ve birim testler bunu goremez -- nesneyi
    // dogrudan kuruyorlar. Ayni tuzak LoginAttemptService'te bir kez yasandi.
    @Autowired
    public LeaveEntitlementAccrual(LeaveEntitlementService entitlements) {
        this(entitlements, Clock.systemDefaultZone());
    }

    /**
     * Testler icin: yili kontrol edilebilir kilar.
     *
     * Saat UTC DEGIL, sistem saat dilimi: burada sorulan sey "hangi takvim
     * yilindayiz" ve o soru yerel takvime gore cevaplanir. UTC'de 31 Aralik
     * 23:00, Istanbul'da yeni yildir.
     */
    LeaveEntitlementAccrual(LeaveEntitlementService entitlements, Clock clock) {
        this.entitlements = entitlements;
        this.clock = clock;
    }

    @Scheduled(
            initialDelayString = "${app.leave.accrual.initial-delay-ms:60000}",
            fixedDelayString = "${app.leave.accrual.interval-ms:86400000}")
    public void accrueCurrentYear() {
        int year = LocalDate.now(clock).getYear();

        try {
            entitlements.accrueFor(year);
        } catch (RuntimeException ex) {
            // Yutulmaz ama yayilmasi da engellenir: firlatan bir @Scheduled
            // metot yalnizca kendi turunu kaybeder, ama sebebi loglanmazsa
            // hicbir yerde iz kalmaz. Yarin tekrar denenecek.
            log.error("Leave entitlement accrual failed for {}", year, ex);
        }
    }
}
