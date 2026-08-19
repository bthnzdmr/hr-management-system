package com.proje.employee.service;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.config.LeavePolicy;
import com.proje.employee.dto.LeaveBalanceResponse;
import com.proje.employee.dto.LeaveEntitlementRequest;
import com.proje.employee.dto.LeaveEntitlementResponse;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveEntitlement;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveEntitlementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Yillik izin hakkini VEREN taraf; okuyan taraf `LeaveBalanceService`.
 *
 * Hak iki yoldan olusur: tahakkuk isi kidem merdivenine gore yazar, Ik
 * sonradan duzeltir. Ikisi ayni satiri hedefler ve Ik'nin yazdigi KAZANIR --
 * is yalnizca EKSIK satirlari doldurur.
 */
@Service
public class LeaveEntitlementService {

    private static final Logger log = LoggerFactory.getLogger(LeaveEntitlementService.class);

    private final LeaveEntitlementRepository entitlements;
    private final EmployeeRepository employees;
    private final LeaveBalanceService balances;
    private final LeavePolicy policy;

    public LeaveEntitlementService(LeaveEntitlementRepository entitlements,
                                   EmployeeRepository employees,
                                   LeaveBalanceService balances,
                                   LeavePolicy policy) {
        this.entitlements = entitlements;
        this.employees = employees;
        this.balances = balances;
        this.policy = policy;
    }

    /**
     * Ik'nin acik karari; varsa uzerine yazar.
     *
     * Uc IDEMPOTENT ve butun degerleri birden alir. "Gun ekle" / "gun cikar"
     * gibi artimli uclar acilsaydi iki es zamanli istek birbirinin uzerine
     * yazabilir ve kimsenin istemedigi bir ara duruma dusulebilirdi -- rol
     * kumesinin komple gonderilmesindeki gerekcenin aynisi.
     */
    @Auditable(action = AuditAction.LEAVE_ENTITLEMENT_SET, targetType = "LEAVE_ENTITLEMENT")
    @Transactional
    public LeaveEntitlementResponse set(Long employeeId, int year, LeaveEntitlementRequest request) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        LeaveEntitlement entitlement = entitlements.findByEmployeeIdAndYear(employeeId, year)
                .map(existing -> {
                    existing.adjust(request.entitledDays(), request.carriedOverDays(),
                            request.note());
                    return existing;
                })
                .orElseGet(() -> entitlements.save(new LeaveEntitlement(employee, year,
                        request.entitledDays(), request.carriedOverDays(), request.note())));

        return LeaveEntitlementResponse.from(entitlement);
    }

    /**
     * Eksik hak satirlarini yazar; var olanlara DOKUNMAZ.
     *
     * Dokunmamasi kritik: is her gun calisiyor ve var olan satirin uzerine
     * yazsaydi Ik'nin elle yaptigi duzeltme ertesi gun SESSIZCE geri alinirdi.
     * Idempotentlik burada bir incelik degil, ozelligin dogru calismasinin
     * on kosulu.
     *
     * @return yazilan satir sayisi
     */
    @Transactional
    public int accrueFor(int year) {
        List<Employee> pending = entitlements.findActiveWithoutEntitlement(year);
        if (pending.isEmpty()) {
            return 0;
        }

        LocalDate endOfYear = LocalDate.of(year, 12, 31);

        for (Employee employee : pending) {
            int entitled = policy.entitledDaysFor(completedYears(employee, endOfYear));
            int carriedOver = policy.carryOverFrom(remainingFrom(employee.getId(), year - 1));

            entitlements.save(new LeaveEntitlement(employee, year, entitled, carriedOver,
                    "Accrued automatically"));
        }

        log.info("Accrued leave entitlement for {} employee(s) for {}", pending.size(), year);

        return pending.size();
    }

    /**
     * Yil SONUNDA tamamlanmis hizmet yili.
     *
     * Once yil BASINDA olculuyordu ve olcum bunun yanlis oldugunu gosterdi:
     * 33 kisiden 9'u sifir gun aliyordu, oysa besi o yil icinde bir yilini
     * DOLDURUYOR. Alti kisi de besinci yilini o yil doldurup 14 gunde
     * kaliyordu.
     *
     * Kanunen hak YILDONUMUNDE dogar; takvim yilina yazilan tek bir satir bunu
     * gun gun ifade edemez, dolayisiyla yuvarlamak zorundayiz. Yil sonuna
     * yuvarlamak calisan LEHINE sapar ve bu hukuken guvenlidir: asgarinin
     * ustune cikilabilir, altina inilemez.
     *
     * KALAN SINIR: hak orantilanmiyor. Aralik'ta bir yilini dolduran da tam
     * 14 gun alir. Ik'nin elle duzeltme yolu tam da bunun icin acik.
     */
    private int completedYears(Employee employee, LocalDate endOfYear) {
        if (employee.getHireDate().isAfter(endOfYear)) {
            return 0;
        }

        return Period.between(employee.getHireDate(), endOfYear).getYears();
    }

    /**
     * Onceki yildan kalan gun.
     *
     * Hesap SQL'de tekrarlanmiyor, `LeaveBalanceService` cagriliyor: ayni kural
     * iki yerde yasasaydi biri zamanla geride kalirdi -- bu projede tam olarak
     * dort kez yasanmis bir hata.
     *
     * Bedeli personel basina birkac sorgu. Kabul edilebilir: is gunde bir kez
     * ve yalnizca EKSIK satirlar icin calisiyor, yani kadro bir kez tarandiktan
     * sonra liste bos doner.
     */
    private int remainingFrom(Long employeeId, int previousYear) {
        LeaveBalanceResponse previous = balances.balanceFor(employeeId, previousYear);

        // YONETILMEMIS bir yildan devir tasinmaz. Hak satiri yoksa bakiye
        // yapilandirmadaki varsayilandan hesaplanir ve o sayi bir KARAR degil,
        // bir tahmindir; ondan devretmek olmayan bir hakki uretmek olurdu.
        //
        // Olculdu: ilk tahakkuk kosusunda 33 kisinin hepsi, hak satiri hic
        // olmayan bir yildan 5 gun devralmisti.
        if (previous.source() != LeaveBalanceResponse.Source.GRANTED) {
            return 0;
        }

        return previous.availableDays();
    }
}
