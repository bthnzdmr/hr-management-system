package com.proje.employee.service;

import com.proje.employee.dto.LeaveBalanceResponse;
import com.proje.employee.entity.LeaveEntitlement;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveEntitlementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Yillik izin bakiyesi.
 *
 * Bakiye SAKLANMAZ, her sorguda hesaplanir. Saklansaydi izin onaylandiginda,
 * reddedildiginde, geri cekildiginde ve hak duzeltildiginde ayri ayri
 * guncellenmesi gerekirdi -- ve bir yol unutuldugunda sayi sessizce yanlis
 * kalirdi. Turetilmis deger, kaynagindan hesaplanir.
 */
@Service
public class LeaveBalanceService {

    private final LeaveEntitlementRepository entitlementRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeVisibility visibility;
    private final int defaultAnnualDays;

    public LeaveBalanceService(LeaveEntitlementRepository entitlementRepository,
                               EmployeeRepository employeeRepository,
                               EmployeeVisibility visibility,
                               @Value("${app.leave.default-annual-days}") int defaultAnnualDays) {
        this.entitlementRepository = entitlementRepository;
        this.employeeRepository = employeeRepository;
        this.visibility = visibility;
        this.defaultAnnualDays = defaultAnnualDays;
    }

    /**
     * Kapsam denetimli bakiye.
     *
     * Kapsam disindaki kayit icin 404 doner, 403 DEGIL: 403 "bu kayit var ama
     * goremezsin" der ve id deneyerek personel varligi ogrenilebilirdi. Ayni
     * karar `getById`de bir kez verilmisti.
     */
    @Transactional(readOnly = true)
    public LeaveBalanceResponse balanceFor(Long employeeId, int year, AccessScope scope) {
        if (!visibility.canSee(employeeId, scope)) {
            throw new EmployeeNotFoundException(employeeId);
        }

        return balanceFor(employeeId, year);
    }

    /**
     * Bakiyeyi hesaplar.
     *
     * Tum sorgular AYNI transaction'da: hak ile kullanim ayri anlarda
     * okunsaydi, arada onaylanan bir izin "hak 20, kullanilan 21" gibi
     * kendisiyle celisen bir tablo uretebilirdi.
     */
    @Transactional(readOnly = true)
    public LeaveBalanceResponse balanceFor(Long employeeId, int year) {
        if (!employeeRepository.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }

        Optional<LeaveEntitlement> granted =
                entitlementRepository.findByEmployeeIdAndYear(employeeId, year);

        int entitled = granted.map(LeaveEntitlement::getEntitledDays).orElse(defaultAnnualDays);
        int carriedOver = granted.map(LeaveEntitlement::getCarriedOverDays).orElse(0);

        var usage = entitlementRepository.findAnnualUsage(employeeId, year);
        int used = usage.getUsed();
        int reserved = usage.getReserved();

        return new LeaveBalanceResponse(
                employeeId,
                year,
                entitled,
                carriedOver,
                used,
                reserved,
                entitled + carriedOver - used - reserved,
                granted.isPresent()
                        ? LeaveBalanceResponse.Source.GRANTED
                        : LeaveBalanceResponse.Source.DEFAULT);
    }
}
