package com.proje.employee.export;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.service.AccessScope;
import com.proje.employee.service.EmployeeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Personel listesini CSV olarak verir.
 *
 * Kapsam AYNEN uygulanir: cagiran, tek tek goremedigi bir satiri toplu halde
 * de goremez. Sorgu `EmployeeService.getAll` uzerinden gidiyor, kendi
 * sorgusunu yazmiyor -- ayni kural iki yerde yasasaydi biri zamanla geride
 * kalirdi.
 *
 * UCRET YOK. Butun tasarim maasi dar bir yetki cemberinde tutuyor; disari
 * aktarma o cemberi delen bir arka kapi olamaz.
 */
@Service
public class EmployeeExportService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final List<String> HEADER = List.of(
            "Id", "First name", "Last name", "Email", "Phone",
            "Department", "Job title", "Manager", "Hire date",
            "Status", "Left on", "Leave reason");

    private final EmployeeService employees;
    private final int maxRows;

    public EmployeeExportService(EmployeeService employees,
                                 @Value("${app.export.max-rows}") int maxRows) {
        this.employees = employees;
        this.maxRows = maxRows;
    }

    /**
     * Transaction READONLY DEGIL ve bu zorunlu.
     *
     * Sorgularin hepsi okuma ama `@Auditable` denetim satirini AYNI
     * transaction'a yaziyor; salt okunur bir transaction'da yazma onu
     * rollback-only isaretler ve commit aninda `UnexpectedRollbackException`
     * firlar. OLCULDU: 441 test yesilken uc 500 donuyordu.
     *
     * Bu, kod tabanindaki ILK denetlenen OKUMA -- denetim bugune kadar
     * yalnizca yazma uclarindaydi ve varsayim hic catismamisti.
     *
     * @throws ExportTooLargeException sonuc tavani asiyorsa
     */
    @Auditable(action = AuditAction.EMPLOYEES_EXPORTED, targetType = "EMPLOYEE")
    @Transactional
    public String toCsv(String search, Boolean active, AccessScope scope) {
        // Sayfa boyutunu SUNUCU belirliyor, istemci degil. `?size=` ile
        // butun dizini tek cevapta cekmek `max-page-size: 100` ile kapatilmisti
        // ve disari aktarma o kapiyi geri acmamali.
        Page<EmployeeResponse> page = employees.getAll(search, active, scope,
                PageRequest.of(0, maxRows + 1, Sort.by("lastName").ascending()));

        // SESSIZ KIRPMA YOK. Eksik oldugunu soylemeyen bir dosya, eksik
        // dosyadan kotudur: kullanici tam sanip karar verirdi.
        if (page.getNumberOfElements() > maxRows) {
            throw new ExportTooLargeException(page.getTotalElements(), maxRows);
        }

        List<List<String>> rows = new ArrayList<>(page.getNumberOfElements());
        page.forEach(employee -> rows.add(row(employee)));

        return CsvWriter.toCsv(HEADER, rows);
    }

    private List<String> row(EmployeeResponse employee) {
        return Arrays.asList(
                String.valueOf(employee.id()),
                employee.firstName(),
                employee.lastName(),
                employee.email(),
                employee.phone(),
                employee.departmentName(),
                employee.jobTitle(),
                employee.managerFullName(),
                employee.hireDate() == null ? null : ISO.format(employee.hireDate()),
                employee.active() ? "Active" : "Left",
                employee.terminatedAt() == null ? null : ISO.format(employee.terminatedAt()),
                employee.terminationReason() == null ? null : employee.terminationReason().name());
    }
}
