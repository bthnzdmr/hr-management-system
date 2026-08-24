package com.proje.employee.controller;

import com.proje.employee.export.EmployeeExportService;
import com.proje.employee.export.EmployeeImportService;
import com.proje.employee.export.ImportReport;
import com.proje.employee.service.AccessScopeResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@RequestMapping("/api")
@Tag(name = "Export", description = "Listelerin CSV olarak disari aktarilmasi")
public class ExportController {

    private final EmployeeExportService employees;
    private final EmployeeImportService imports;
    private final AccessScopeResolver scopes;

    public ExportController(EmployeeExportService employees, EmployeeImportService imports,
                            AccessScopeResolver scopes) {
        this.employees = employees;
        this.imports = imports;
        this.scopes = scopes;
    }

    @GetMapping(value = "/exports/employees", produces = "text/csv")
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

        String csv = employees.toCsv(search, active, scopes.resolve(principal)).csv();
        String filename = "employees-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
                // Tarayici dosyayi ACMAK yerine INDIRSIN. Eksik olsaydi CSV
                // sekmede metin olarak gorunurdu.
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(value = "/imports/employees", consumes = "text/csv")
    @Operation(summary = "Personel listesini CSV'den yukler",
            description = """
                    HEPSI YA DA HICBIRI: tek bir gecersiz satir butun dosyayi
                    reddeder ve hicbir sey yazilmaz. Kismi yukleme operatoru
                    bilinmeyen bir duruma birakirdi -- 500 satirin kacinin
                    girdigi belli olmaz ve tekrar denemek mukerrer kayit
                    riski tasir.

                    OLAY YAYINLANMAZ: toplu yukleme cogu zaman bir GOC
                    islemidir ve yuzlerce kisiye yanlislikla "hos geldiniz"
                    maili gondermek geri alinamaz.

                    Ucret sutunu YOK: kaydi acan kisinin ucret atamasi ayri
                    bir yetkidir.
                    """)
    @ApiResponse(responseCode = "422",
            description = "Dosya reddedildi; govde satir satir gerekceyi tasir")
    public ImportReport employees(@RequestBody String csv) {
        return imports.importFrom(csv);
    }
}
