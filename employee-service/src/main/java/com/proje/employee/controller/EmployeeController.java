package com.proje.employee.controller;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.EmployeeStatusRequest;
import com.proje.employee.dto.EmployeeUpdateRequest;
import com.proje.employee.dto.SalaryResponse;
import com.proje.employee.dto.SalaryUpdateRequest;
import com.proje.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public Page<EmployeeResponse> getAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return employeeService.getAll(search, active, pageable);
    }

    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable Long id) {
        return employeeService.getById(id);
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(
            @Valid @RequestBody EmployeeCreateRequest request) {

        EmployeeResponse response = employeeService.create(request);

        return ResponseEntity
                .created(URI.create("/api/employees/" + response.id()))
                .body(response);
    }

    @PutMapping("/{id}")
    public EmployeeResponse update(@PathVariable Long id,
                                   @Valid @RequestBody EmployeeUpdateRequest request) {

        return employeeService.update(id, request);
    }

    /**
     * Pasiflestirme ve yeniden aktiflestirme.
     *
     * DELETE degil: kayit silinmiyor, durumu degisiyor. DELETE "sildim" derdi,
     * oysa veri duruyor ve geri alinabiliyor -- ve geri donus yolu olmadan
     * pasiflestirme tek yonlu bir kapiydi.
     */
    @PutMapping("/{id}/status")
    public EmployeeResponse changeStatus(@PathVariable Long id,
                                         @Valid @RequestBody EmployeeStatusRequest request) {

        return employeeService.changeStatus(id, request.active());
    }

    // Ekip gorunumu ve pasiflestirme uyarisi icin. Okuma oldugu icin giris
    // yapmis her kullaniciya acik.
    @GetMapping("/{id}/direct-reports")
    public List<EmployeeResponse> getDirectReports(@PathVariable Long id) {
        return employeeService.getDirectReports(id);
    }

    // Maas ayri bir alt kaynaktir: genel personel cevabinda donmez ve genel
    // guncelleme onu tasimaz. Yetki SecurityConfig'te uc bazinda ADMIN'e kisitli.
    @GetMapping("/{id}/salary")
    public SalaryResponse getSalary(@PathVariable Long id) {
        return employeeService.getSalary(id);
    }

    @PutMapping("/{id}/salary")
    public SalaryResponse updateSalary(@PathVariable Long id,
                                       @Valid @RequestBody SalaryUpdateRequest request) {

        return employeeService.updateSalary(id, request);
    }
}
