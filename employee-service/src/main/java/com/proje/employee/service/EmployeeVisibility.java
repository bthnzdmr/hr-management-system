package com.proje.employee.service;

import com.proje.employee.entity.Employee;
import com.proje.employee.repository.EmployeeRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * "Bu kapsam kimin kaydini gorebilir?" sorusunun TEK cevabi.
 *
 * Kural once `LeaveRequestService` icinde ozeldi. Bakiye ucu de ayni kurali
 * istedigi anda tekrar yazilacakti -- ve ayni kurali iki yere yazmak, birinin
 * zamanla geride kalmasi demektir. Bu proje o hatayi pasif yonetici, ayrilmis
 * personele hesap ve pasif departman kurallarinda tam UC kez yasadi.
 */
@Component
public class EmployeeVisibility {

    private final EmployeeRepository employees;

    public EmployeeVisibility(EmployeeRepository employees) {
        this.employees = employees;
    }

    /**
     * Gorulebilir personel kimlikleri; sinirsiz kapsamda `null`.
     *
     * `null` "filtre yok" demektir, "hicbiri" degil -- cagiran taraf bunu
     * sorguya filtre koymadan gecirir.
     */
    public List<Long> visibleEmployeeIds(AccessScope scope) {
        if (scope.isUnrestricted()) {
            return null;
        }

        Long self = scope.employeeId();

        if (!scope.includesDirectReports()) {
            return List.of(self);
        }

        // Yonetici kendi kaydini ve DOGRUDAN astlarini gorur; torunlari DEGIL.
        // Ayni sizinti org chart ucunda bir kez kapatilmisti.
        List<Long> visible = new java.util.ArrayList<>();
        visible.add(self);
        employees.findByManagerIdOrderByLastNameAsc(self).stream()
                .map(Employee::getId)
                .forEach(visible::add);

        return List.copyOf(visible);
    }

    /** Verilen personel bu kapsamda gorunuyor mu? */
    public boolean canSee(Long employeeId, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return true;
        }
        if (scope.isEmpty()) {
            return false;
        }

        return visibleEmployeeIds(scope).contains(employeeId);
    }
}
