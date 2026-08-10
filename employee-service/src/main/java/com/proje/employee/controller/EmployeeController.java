package com.proje.employee.controller;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public Page<EmployeeResponse> getAll(
            @PageableDefault(size = 20, sort = "lastName", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return employeeService.getAll(pageable);
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        employeeService.deactivate(id);
        return ResponseEntity.noContent().build();
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
