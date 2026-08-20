package com.proje.employee.export;

import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.service.AccessScope;
import com.proje.employee.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Personel listesinin CSV olarak aktarilmasi. */
@ExtendWith(MockitoExtension.class)
class EmployeeExportServiceTest {

    private static final int MAX_ROWS = 10;

    @Mock
    private EmployeeService employees;

    private EmployeeExportService service;

    @BeforeEach
    void setUp() {
        service = new EmployeeExportService(employees, MAX_ROWS);
    }

    private EmployeeResponse person(long id, String first, String last) {
        return new EmployeeResponse(id, 0L, first, last, first + "@example.com", null,
                1L, "Software Development", null, null, "Engineer",
                LocalDate.of(2030, 1, 15), true, null, null);
    }

    private void serverReturns(List<EmployeeResponse> found) {
        when(employees.getAll(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(found));
    }

    private ExportResult result() {
        return service.toCsv(null, null, new AccessScope(AccessScope.Kind.ALL, null, false));
    }

    private String export() {
        return result().csv();
    }

    @Test
    @DisplayName("Writes one row per person plus a header")
    void writesARowPerPerson() {
        serverReturns(List.of(person(1, "Grace", "Hopper"), person(2, "Ada", "Lovelace")));

        String csv = export();

        assertThat(csv.split("\r\n")).hasSize(3);
        assertThat(csv).contains("Grace").contains("Ada");
    }

    @Test
    @DisplayName("Never writes salary, not even as an empty column")
    void salaryIsNeverWritten() {
        // Butun tasarim maasi dar bir yetki cemberinde tutuyor; disari aktarma
        // o cemberi delen bir arka kapi olamaz. Bos bir sutun bile yanlis
        // olurdu: yarin birileri onu doldurmayi "eksik" sanip tamamlardi.
        serverReturns(List.of(person(1, "Grace", "Hopper")));

        assertThat(export().toLowerCase()).doesNotContain("salary");
    }

    @Test
    @DisplayName("Applies the caller's scope instead of querying everything")
    void theCallersScopeIsApplied() {
        // Cagiran, tek tek goremedigi bir satiri TOPLU halde de gormemeli.
        // Kendi sorgusunu yazmak yerine EmployeeService cagriliyor: ayni kural
        // iki yerde yasasaydi biri zamanla geride kalirdi.
        AccessScope team = new AccessScope(AccessScope.Kind.TEAM, 42L, false);
        serverReturns(List.of());

        service.toCsv("hop", true, team);

        ArgumentCaptor<AccessScope> used = ArgumentCaptor.forClass(AccessScope.class);
        org.mockito.Mockito.verify(employees)
                .getAll(eq("hop"), eq(true), used.capture(), any());
        assertThat(used.getValue()).isEqualTo(team);
    }

    @Test
    @DisplayName("The page size comes from the server, never from the caller")
    void theServerChoosesThePageSize() {
        // `?size=` ile butun dizini tek cevapta cekmek max-page-size ile
        // kapatilmisti; aktarma o kapiyi geri acmamali.
        serverReturns(List.of());

        export();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(employees).getAll(any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        // Tavandan BIR FAZLA isteniyor: asildigini anlamanin tek yolu bu.
        assertThat(pageable.getValue().getPageSize()).isEqualTo(MAX_ROWS + 1);
    }

    @Test
    @DisplayName("A result over the limit is refused, never silently trimmed")
    void tooManyRowsAreRefused() {
        // SESSIZ KIRPMA YOK. Eksik oldugunu soylemeyen bir dosya eksik
        // dosyadan kotudur: kullanici tam sanip karar verirdi. Ayni ilke izin
        // takviminde ve organizasyon haritasinda da uygulandi.
        List<EmployeeResponse> tooMany = new ArrayList<>(
                IntStream.rangeClosed(1, MAX_ROWS + 1)
                        .mapToObj(i -> person(i, "Person" + i, "Test"))
                        .toList());
        serverReturns(tooMany);

        assertThatThrownBy(this::result)
                .isInstanceOf(ExportTooLargeException.class)
                .hasMessageContaining("Narrow the search");
    }

    @Test
    @DisplayName("A result exactly at the limit is still written")
    void exactlyTheLimitIsAllowed() {
        // Sinir kosulu: karsilastirma ">" olmali, ">=" degil. Yanlis operator
        // tam tavanda duran bir aktarmayi sebepsiz reddederdi.
        serverReturns(IntStream.rangeClosed(1, MAX_ROWS)
                .mapToObj(i -> person(i, "Person" + i, "Test"))
                .toList());

        assertThat(export().split("\r\n")).hasSize(MAX_ROWS + 1);
    }

    @Test
    @DisplayName("An empty result is a valid file with only a header")
    void anEmptyResultIsStillAFile() {
        // Hata donmek yanlis olurdu: "kimse yok" gecerli bir cevaptir ve
        // basliklar dosyanin bozuk OLMADIGINI soyluyor.
        serverReturns(List.of());

        assertThat(export()).contains("Id,First name,Last name");
    }

    @Test
    @DisplayName("A name that looks like a formula is neutralised on the way out")
    void aFormulaLikeNameIsNeutralised() {
        // Uctan uca: veri girisi bunu engellemiyor (bir soyad gercekten
        // "-Reyes" olabilir), koruma CIKISTA.
        serverReturns(List.of(person(1, "=cmd|'/c calc'!A0", "Hopper")));

        assertThat(export()).doesNotContain(",=cmd").contains("'=cmd");
    }

    @Test
    @DisplayName("Rows are ordered so two exports of the same data match")
    void rowsAreOrdered() {
        // Siralama yazilmasaydi veritabani sirasi degistiginde iki aktarma
        // farkli cikar ve karsilastirilamazdi.
        serverReturns(List.of());

        export();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(employees).getAll(any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getSort().isSorted()).isTrue();
    }
}
