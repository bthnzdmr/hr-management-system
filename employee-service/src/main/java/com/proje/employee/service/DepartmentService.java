package com.proje.employee.service;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.dto.DepartmentCreateRequest;
import com.proje.employee.dto.DepartmentResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.exception.DepartmentNameAlreadyExistsException;
import com.proje.employee.exception.DepartmentNotFoundException;
import com.proje.employee.exception.DepartmentRuleViolationException;
import com.proje.employee.repository.DepartmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentService {

    private static final Logger log = LoggerFactory.getLogger(DepartmentService.class);

    private final DepartmentRepository departmentRepository;

    public DepartmentService(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * Departman listesi.
     *
     * Sayfalama yoktur: departman sayisi kurumsal olarak sinirlidir ve liste
     * bir acilir kutuyu doldurmak icin kullanilir. Buyuyebilen listelerde
     * (personel gibi) sayfalama zorunludur.
     *
     * Varsayilan yalnizca AKTIF: secim listesine pasif departman dusmemeli.
     * Yonetim ekrani hepsini ister, cunku kapatilmis olani geri acabilmek
     * icin once gorebilmesi gerekir.
     */
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getAll(boolean includeInactive) {
        List<Department> departments = includeInactive
                ? departmentRepository.findAllByOrderByNameAsc()
                : departmentRepository.findByActiveTrueOrderByNameAsc();

        // Sayimlar TEK sorguda alinir; departman basina ayri sayim N+1 olurdu.
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : departmentRepository.countActiveEmployeesPerDepartment()) {
            counts.put((Long) row[0], (Long) row[1]);
        }

        return departments.stream()
                .map(department -> new DepartmentResponse(
                        department.getId(),
                        department.getName(),
                        department.isActive(),
                        counts.getOrDefault(department.getId(), 0L)))
                .toList();
    }

    @Transactional
    @Auditable(action = AuditAction.DEPARTMENT_CREATED, targetType = "DEPARTMENT")
    public DepartmentResponse create(DepartmentCreateRequest request) {
        String name = request.name().trim();

        // Kullaniciya anlamli mesaj donmek icin. Dogruluk garantisi bu kontrol
        // degil, veritabanindaki uk_department_name kisitidir.
        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new DepartmentNameAlreadyExistsException(name);
        }

        Department saved = departmentRepository.save(new Department(name));

        return new DepartmentResponse(saved.getId(), saved.getName(), saved.isActive(), 0);
    }

    @Transactional
    @Auditable(action = AuditAction.DEPARTMENT_STATUS_CHANGED, targetType = "DEPARTMENT")
    public DepartmentResponse changeStatus(Long id, boolean active) {
        // KILITLEYEREK okunur: "once say, bos ise kapat" bir check-then-act
        // olurdu ve eszamanli bir atama pasif departmanda aktif personel
        // birakabilirdi.
        Department department = departmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new DepartmentNotFoundException(id));

        // Uc IDEMPOTENT: zaten istenen durumdaysa hicbir sey yazilmaz.
        // Personel durum ucuyle ayni gerekce.
        if (department.isActive() == active) {
            return toResponse(department);
        }

        if (!active) {
            long employees = departmentRepository.countActiveEmployees(id);
            if (employees > 0) {
                // Mesaj NE YAPILMASI gerektigini soyler; "kapatilamaz" demek
                // kullaniciyi cikissiz birakirdi.
                throw new DepartmentRuleViolationException(
                        "This department still has " + employees + " active "
                                + (employees == 1 ? "employee" : "employees")
                                + ". Move them to another department first.");
            }
        }

        department.setActive(active);
        log.info("Department {} was {}", id, active ? "reopened" : "closed");

        return toResponse(department);
    }

    private DepartmentResponse toResponse(Department department) {
        return new DepartmentResponse(
                department.getId(),
                department.getName(),
                department.isActive(),
                departmentRepository.countActiveEmployees(department.getId()));
    }
}
