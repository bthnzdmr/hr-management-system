package com.proje.employee.controller;

import com.proje.employee.dto.DepartmentResponse;
import java.net.URI;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import com.proje.employee.dto.DepartmentStatusRequest;
import com.proje.employee.dto.DepartmentCreateRequest;
import com.proje.employee.service.DepartmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Departments", description = "Referans verisi.")
@RestController
@RequestMapping("/api/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    @GetMapping
    @Operation(summary = "Departman listesi",
            description = "Varsayilan yalnizca aktif olanlar: secim listesine kapatilmis "
                    + "bir departman dusmemelidir.")
    public List<DepartmentResponse> getAll(
            @RequestParam(defaultValue = "false") boolean includeInactive) {

        return departmentService.getAll(includeInactive);
    }

    @PostMapping
    @Operation(summary = "Departman ac")
    @ApiResponse(responseCode = "201", description = "Olusturuldu")
    @ApiResponse(responseCode = "409", description = "Bu adda bir departman zaten var")
    public ResponseEntity<DepartmentResponse> create(
            @Valid @RequestBody DepartmentCreateRequest request) {

        DepartmentResponse created = departmentService.create(request);

        return ResponseEntity
                .created(URI.create("/api/departments/" + created.id()))
                .body(created);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Departmani ac veya kapat",
            description = """
                    DELETE degil: kayit silinmiyor, bir alan degisiyor -- ve silmenin
                    geri donusu olmazdi. Uc idempotenttir; zaten istenen durumdaysa
                    hicbir sey yazmaz.
                    """)
    @ApiResponse(responseCode = "400",
            description = "Icinde hala aktif personel var. Mesaj ne yapilmasi gerektigini soyler.")
    public DepartmentResponse changeStatus(@PathVariable Long id,
                                           @Valid @RequestBody DepartmentStatusRequest request) {

        return departmentService.changeStatus(id, request.active());
    }
}
