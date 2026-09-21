package com.proje.employee.export;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CSV'den toplu yukleme.
 *
 * <p>GERCEK veritabanina karsi kosuyor ve bu sart: sinanan seyin cekirdegi
 * <b>hepsi ya da hicbiri</b> ve o garanti transaction sinirinda yasiyor.
 * Taklit edilmis bir repository'de geri alma diye bir sey yoktur -- ayni ders
 * `noRollbackFor` ve `REQUIRES_NEW` vakalarinda iki kez alinmisti.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EmployeeImportService.class)
@TestPropertySource(properties = "app.import.max-rows=5")
class EmployeeImportServiceTest {

    private static final String HEADER =
            "First name,Last name,Email,Phone,Department,Job title,Hire date,Manager email";

    @Autowired
    private EmployeeImportService service;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private EntityManager entityManager;

    private String openDepartment;

    @BeforeEach
    void setUp() {
        entityManager.createQuery("DELETE FROM LeaveEntitlement").executeUpdate();
        entityManager.createQuery("DELETE FROM LeaveRequest").executeUpdate();
        entityManager.createQuery("UPDATE User u SET u.employee = null").executeUpdate();
        employees.deleteAllInBatch();

        openDepartment = departments.findAll().stream()
                .filter(Department::isActive)
                .findFirst()
                .orElseGet(() -> departments.save(new Department("Imports")))
                .getName();
    }

    private String file(String... rows) {
        return HEADER + "\r\n" + String.join("\r\n", rows) + "\r\n";
    }

    private String row(String first, String last, String email) {
        return row(first, last, email, openDepartment, "2024-03-01", "");
    }

    private String row(String first, String last, String email,
                       String department, String hireDate, String managerEmail) {
        return String.join(",", first, last, email, "", department, "Engineer",
                hireDate, managerEmail);
    }

    private long count() {
        return employees.count();
    }

    /**
     * Reddi yakalar ve raporunu doner.
     *
     * Ret DAIMA istisnadir: normal donus kullanildiginda hicbir sey
     * yazilmamis bir istek `200` donuyordu ve bunu ancak canli olcum yakaladi.
     */
    private ImportReport rejection(String csv) {
        try {
            service.importFrom(csv);
        } catch (ImportRejectedException rejected) {
            return rejected.report();
        }
        throw new AssertionError("The file should have been rejected");
    }

    // ------------------------------------------------------------- kabul

    @Test
    @DisplayName("Loads every row of a good file")
    void loadsAGoodFile() {
        ImportReport report = service.importFrom(file(
                row("Grace", "Hopper", "grace@example.com"),
                row("Ada", "Lovelace", "ada@example.com")));

        assertThat(report.imported()).isEqualTo(2);
        assertThat(report.isRejected()).isFalse();
        assertThat(count()).isEqualTo(2);
    }

    @Test
    @DisplayName("Reads a file the export itself produced")
    void readsWhatTheExportWrites() {
        // Baslik satiri ELLE yazilmiyor: iki taraf birbirinden ayrilirsa bu
        // test kirilir. Ayrica dosya BOM ile basliyor.
        String exported = CsvWriter.toCsv(EmployeeImportService.HEADER,
                java.util.List.of(java.util.List.of("Grace", "Hopper", "grace@example.com",
                        "", openDepartment, "Engineer", "2024-03-01", "")));

        assertThat(service.importFrom(exported).imported()).isEqualTo(1);
    }

    @Test
    @DisplayName("An optional phone and manager may be left empty")
    void optionalColumnsMayBeEmpty() {
        service.importFrom(file(row("Grace", "Hopper", "grace@example.com")));

        Employee saved = employees.findByEmail("grace@example.com").orElseThrow();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getManager()).isNull();
    }

    // ------------------------------------------------------------- yonetici

    @Test
    @DisplayName("A manager listed further down the same file is still found")
    void aManagerLowerInTheFileIsFound() {
        // TEK GECIS YETMEZ: satir okunurken yoneticisi henuz yazilmamis olur.
        service.importFrom(file(
                row("Ada", "Lovelace", "ada@example.com", openDepartment, "2024-03-01",
                        "grace@example.com"),
                row("Grace", "Hopper", "grace@example.com")));

        assertThat(employees.findByEmail("ada@example.com").orElseThrow().getManager())
                .isNotNull()
                .extracting(Employee::getEmail).isEqualTo("grace@example.com");
    }

    @Test
    @DisplayName("A manager who already exists in the system is found too")
    void anExistingManagerIsFound() {
        Department department = departments.findByName(openDepartment).orElseThrow();
        employees.save(new Employee("Grace", "Hopper", "grace@example.com",
                department, "Engineer", LocalDate.of(2020, 1, 1)));

        service.importFrom(file(row("Ada", "Lovelace", "ada@example.com",
                openDepartment, "2024-03-01", "grace@example.com")));

        assertThat(employees.findByEmail("ada@example.com").orElseThrow().getManager())
                .isNotNull();
    }

    @Test
    @DisplayName("An unknown manager rejects the file and writes nothing")
    void anUnknownManagerRejectsEverything() {
        // Yonetici cozumu ancak yazdiktan SONRA yapilabiliyor; oradaki hata
        // transaction'i geri almazsa yoneticisiz YARIM bir yukleme kalirdi.
        assertThatThrownBy(() -> service.importFrom(file(
                row("Ada", "Lovelace", "ada@example.com", openDepartment, "2024-03-01",
                        "nobody@example.com"))))
                .isInstanceOf(ImportRejectedException.class);
    }

    // ------------------------------------------------------------- ret

    @Test
    @DisplayName("One bad row rejects the whole file and writes nothing")
    void oneBadRowRejectsEverything() {
        // HEPSI YA DA HICBIRI. Kismi yukleme operatoru bilinmeyen bir duruma
        // birakirdi: kacinin girdigi belli olmaz ve tekrar denemek mukerrer
        // kayit riski tasir.
        ImportReport report = rejection(file(
                row("Grace", "Hopper", "grace@example.com"),
                row("", "Lovelace", "ada@example.com")));

        assertThat(report.isRejected()).isTrue();
        assertThat(report.imported()).isZero();
        assertThat(count()).isZero();
    }

    @Test
    @DisplayName("Every problem is reported at once, not one per attempt")
    void everyProblemIsReportedAtOnce() {
        // Ilk hatada durulsaydi kullanici dosyayi satir satir duzeltmek icin
        // onlarca tur atardi.
        ImportReport report = rejection(file(
                row("", "Lovelace", "ada@example.com"),
                row("Grace", "", "grace@example.com")));

        assertThat(report.errors()).hasSize(2);
    }

    @Test
    @DisplayName("The reported line number matches the file, header included")
    void lineNumbersMatchTheFile() {
        // Kullanici dosyayi bir editorde aciyor; numara oradaki satirla
        // ortusmezse gerekce ise yaramaz.
        ImportReport report = rejection(file(
                row("Grace", "Hopper", "grace@example.com"),
                row("", "Lovelace", "ada@example.com")));

        assertThat(report.errors().get(0).line()).isEqualTo(3);
    }

    @Test
    @DisplayName("An email repeated inside the file is caught before writing")
    void aDuplicateInsideTheFileIsCaught() {
        // Veritabani kisiti yalnizca YAZARKEN devreye girer ve o noktada
        // hepsi-ya-da-hicbiri sozu zaten bozulmus olurdu.
        ImportReport report = rejection(file(
                row("Grace", "Hopper", "grace@example.com"),
                row("Ada", "Lovelace", "grace@example.com")));

        assertThat(report.errors()).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("already appears on line 2"));
    }

    @Test
    @DisplayName("An email that already belongs to somebody is refused")
    void anExistingEmailIsRefused() {
        Department department = departments.findByName(openDepartment).orElseThrow();
        employees.save(new Employee("Grace", "Hopper", "grace@example.com",
                department, "Engineer", LocalDate.of(2020, 1, 1)));

        ImportReport report = rejection(
                file(row("Ada", "Lovelace", "grace@example.com")));

        assertThat(report.isRejected()).isTrue();
        assertThat(count()).isEqualTo(1);
    }

    @Test
    @DisplayName("An unknown department is refused instead of created")
    void anUnknownDepartmentIsRefused() {
        // Sessizce olusturmak, yazim hatasiyla departman uretmenin yolu olurdu.
        ImportReport report = rejection(file(row("Grace", "Hopper",
                "grace@example.com", "No Such Department", "2024-03-01", "")));

        assertThat(report.errors()).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("No department named"));
    }

    @Test
    @DisplayName("A closed department cannot take new people")
    void aClosedDepartmentIsRefused() {
        // Mevcut atamalar korunur, YENI baglanti kurulmaz -- tekil olusturma
        // ucundeki kuralin aynisi.
        Department closed = departments.save(new Department("Closed Unit"));
        closed.setActive(false);
        departments.save(closed);

        ImportReport report = rejection(file(row("Grace", "Hopper",
                "grace@example.com", "Closed Unit", "2024-03-01", "")));

        assertThat(report.errors()).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("is closed"));
    }

    @Test
    @DisplayName("A date that is not a date is refused with a readable reason")
    void aBadDateIsRefused() {
        ImportReport report = rejection(file(row("Grace", "Hopper",
                "grace@example.com", openDepartment, "01/03/2024", "")));

        assertThat(report.errors()).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("YYYY-MM-DD"));
    }

    @Test
    @DisplayName("A wrong header is reported once, not once per row")
    void aWrongHeaderIsReportedOnce() {
        // Baslik yanlissa her satir ayni hatayi tekrarlar ve gercek sebep
        // yuzlerce satirlik gurultunun icinde kaybolur.
        ImportReport report = rejection("Name,Email\r\nGrace,grace@example.com\r\n");

        assertThat(report.errors()).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("header must be exactly"));
    }

    @Test
    @DisplayName("A row with the wrong number of columns is refused")
    void aShortRowIsRefused() {
        ImportReport report = rejection(HEADER + "\r\nGrace,Hopper\r\n");

        assertThat(report.errors()).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("Expected 8 columns"));
    }

    @Test
    @DisplayName("An empty file is refused rather than reported as success")
    void anEmptyFileIsRefused() {
        // "0 kayit yuklendi" demek, dosyanin hic okunmadigini gizlerdi.
        assertThat(rejection("").isRejected()).isTrue();
    }

    @Test
    @DisplayName("A file over the limit is refused before any row is read")
    void tooManyRowsAreRefused() {
        String[] rows = new String[6];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = row("Person" + i, "Test", "person" + i + "@example.com");
        }

        ImportReport report = rejection(file(rows));

        assertThat(report.errors()).singleElement()
                .satisfies(e -> assertThat(e.reason()).contains("more than the limit of 5"));
        assertThat(count()).isZero();
    }
}
