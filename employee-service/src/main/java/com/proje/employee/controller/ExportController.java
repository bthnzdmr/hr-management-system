package com.proje.employee.controller;

import com.proje.employee.export.EmployeeExportService;
import com.proje.employee.service.AccessScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;

/**
 * Toplu disari aktarma.
 *
 * Yol AYRI (`/api/exports/...`), `/api/employees/export` DEGIL: ikincisi
 * mevcut `/api/employees/**` okuma kuralinin ONUNE bir kural yazmayi
 * gerektirirdi ve Spring Security ILK eslesen kurali uygular -- sonra
 * yazilsaydi butun dizin her okuyucuya acik kalirdi. Ayni tuzak maas ucunde
 * bir kez yasandi; org chart ucunda ayri yol tam bu yuzden secilmisti.
 */
@RestController
@RequestMapping("/api/exports")
@Tag(name = "Export", description = "Listelerin CSV olarak disari aktarilmasi")
public class ExportController {

    private final EmployeeExportService employees;
    private final AccessScopeResolver scopes;

    public ExportController(EmployeeExportService employees, AccessScopeResolver scopes) {
        this.employees = employees;
        this.scopes = scopes;
    }

    @GetMapping(value = "/employees", produces = "text/csv")
    @Operation(summary = "Personel listesini CSV olarak indirir",
            description = """
                    Suzgecler listeleme ucuyle AYNI; kapsam da aynen uygulanir.

                    UCRET YOK: butun tasarim maasi dar bir yetki cemberinde
                    tutuyor ve disari aktarma o cemberi delen bir arka kapi
                    olamaz.

                    Sayfa boyutu istemciden alinmaz. Sonuc sunucudaki tavani
                    asarsa dosya KIRPILMAZ, istek reddedilir.
                    """)
    @ApiResponse(responseCode = "409", description = "Sonuc tavani asiyor; once suzun")
    public ResponseEntity<byte[]> employees(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) Boolean active,
                                            Principal principal) {

        String csv = employees.toCsv(search, active, scopes.resolve(principal));
        String filename = "employees-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                // Tarayici dosyayi ACMAK yerine INDIRSIN. Eksik olsaydi CSV
                // sekmede metin olarak gorunurdu.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
