package com.proje.employee.export;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Personel listesini CSV'den yukler.
 *
 * <p><b>HEPSI YA DA HICBIRI.</b> Tek bir gecersiz satir butun dosyayi
 * reddeder ve hicbir sey yazilmaz. Kismi yukleme daha "yardimsever" gorunurdu
 * ama operatoru bilinmeyen bir duruma birakirdi: 500 kisilik bir dosyanin
 * kacinin girdigi belli olmaz, tekrar denemek de mukerrer kayit riski tasir.
 * Projenin kendi ilkesi: <i>eksik veri, yanlis veriden iyidir.</i>
 *
 * <p><b>OLAY YAYINLANMAZ.</b> `POST /api/employees` her kayitta bir
 * "hos geldiniz" maili tetikler; toplu yukleme cogu zaman bir GOC islemidir
 * (var olan kadroyu sisteme almak) ve 200 kisiye yanlislikla hos geldin maili
 * gondermek GERI ALINAMAZ. Bildirim gondermemek ise geri alinabilir bir
 * eksikliktir. Ayni gerekceyle tohumlayici da olay yayinlamiyor.
 *
 * <p>UCRET SUTUNU YOK: kayit acan kisinin ucret atamasi sahte personel
 * olusturmanin klasik yoludur ve tekil olusturma ucunda da bilerek yok.
 */
@Service
public class EmployeeImportService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeImportService.class);

    /** Beklenen basliklar; dis aktarmanin yazdigi baslik satirinin alt kumesi. */
    static final List<String> HEADER = List.of(
            "First name", "Last name", "Email", "Phone",
            "Department", "Job title", "Hire date", "Manager email");

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final int maxRows;

    public EmployeeImportService(EmployeeRepository employees, DepartmentRepository departments,
                                 @Value("${app.import.max-rows}") int maxRows) {
        this.employees = employees;
        this.departments = departments;
        this.maxRows = maxRows;
    }

    @Auditable(action = AuditAction.EMPLOYEES_IMPORTED, targetType = "EMPLOYEE")
    @Transactional
    public ImportReport importFrom(String csv) {
        List<List<String>> rows = CsvReader.parse(csv);

        if (rows.isEmpty()) {
            throw reject(List.of(new ImportReport.RowError(0, "The file is empty")));
        }

        List<ImportReport.RowError> errors = new ArrayList<>();

        headerProblem(rows.get(0)).ifPresent(
                reason -> errors.add(new ImportReport.RowError(1, reason)));

        // Baslik yanlissa satirlari cozmeye calismak anlamsiz: her satir ayni
        // hatayi tekrarlar ve gercek sebep 200 satirlik gurultunun icinde
        // kaybolur.
        if (!errors.isEmpty()) {
            throw reject(errors);
        }

        List<List<String>> body = rows.subList(1, rows.size());

        if (body.size() > maxRows) {
            throw reject(List.of(new ImportReport.RowError(0,
                    "The file has " + body.size() + " rows, more than the limit of " + maxRows)));
        }

        List<Draft> drafts = new ArrayList<>();
        // Dosya ICINDEKI mukerrer e-postayi da yakalar: veritabani kisiti
        // yalnizca YAZARKEN devreye girer ve o noktada hepsi-ya-da-hicbiri
        // sozu bozulmus olurdu.
        Map<String, Integer> seenEmails = new HashMap<>();

        for (int index = 0; index < body.size(); index++) {
            int line = index + 2;
            parse(body.get(index), line, seenEmails, errors).ifPresent(drafts::add);
        }

        if (!errors.isEmpty()) {
            throw reject(errors);
        }

        return apply(drafts, errors);
    }

    /**
     * Ret DAIMA istisnayla bildirilir.
     *
     * Once iki yol vardi: dogrulama hatalari normal DONUS, yonetici hatalari
     * istisnaydi. Sonucu OLCULDU -- reddedilen bir dosya `200` donuyordu, yani
     * hicbir sey yazilmamisken istek "basarili" gorunuyordu. Birim testler
     * farki goremezdi cunku donen raporu okuyorlardi, HTTP kodunu degil.
     */
    private ImportRejectedException reject(List<ImportReport.RowError> errors) {
        return new ImportRejectedException(ImportReport.rejected(errors));
    }

    /** Cozulmus ama HENUZ yazilmamis bir satir. */
    private record Draft(int line, String firstName, String lastName, String email, String phone,
                         Department department, String jobTitle, LocalDate hireDate,
                         String managerEmail) {
    }

    private Optional<String> headerProblem(List<String> header) {
        List<String> actual = header.stream().map(String::trim).toList();

        if (!actual.equals(HEADER)) {
            return Optional.of("The header must be exactly: " + String.join(", ", HEADER));
        }

        return Optional.empty();
    }

    private Optional<Draft> parse(List<String> row, int line, Map<String, Integer> seenEmails,
                                  List<ImportReport.RowError> errors) {

        if (row.size() != HEADER.size()) {
            errors.add(new ImportReport.RowError(line,
                    "Expected " + HEADER.size() + " columns but found " + row.size()));
            return Optional.empty();
        }

        String firstName = row.get(0).trim();
        String lastName = row.get(1).trim();
        String email = row.get(2).trim();
        String phone = row.get(3).trim();
        String departmentName = row.get(4).trim();
        String jobTitle = row.get(5).trim();
        String hireDate = row.get(6).trim();
        String managerEmail = row.get(7).trim();

        int before = errors.size();

        require(firstName, "First name", line, errors);
        require(lastName, "Last name", line, errors);
        require(jobTitle, "Job title", line, errors);

        if (email.isEmpty() || !email.contains("@")) {
            errors.add(new ImportReport.RowError(line, "Email is missing or not an address"));
        } else {
            Integer earlier = seenEmails.putIfAbsent(email.toLowerCase(Locale.ROOT), line);

            if (earlier != null) {
                errors.add(new ImportReport.RowError(line,
                        "Email " + email + " already appears on line " + earlier));
            } else if (employees.existsByEmail(email)) {
                errors.add(new ImportReport.RowError(line,
                        "Email " + email + " already belongs to somebody"));
            }
        }

        Department department = null;
        if (departmentName.isEmpty()) {
            errors.add(new ImportReport.RowError(line, "Department is required"));
        } else {
            Optional<Department> found = departments.findByName(departmentName);

            if (found.isEmpty()) {
                errors.add(new ImportReport.RowError(line,
                        "No department named " + departmentName));
            } else if (!found.get().isActive()) {
                // Kapali departmana YENI baglanti kurulmaz; mevcut atamalar
                // korunur. Tekil olusturma ucundeki kuralin aynisi.
                errors.add(new ImportReport.RowError(line,
                        "Department " + departmentName + " is closed"));
            } else {
                department = found.get();
            }
        }

        LocalDate parsedDate = null;
        try {
            parsedDate = LocalDate.parse(hireDate);
        } catch (DateTimeParseException notADate) {
            errors.add(new ImportReport.RowError(line,
                    "Hire date must be written as YYYY-MM-DD"));
        }

        if (errors.size() > before) {
            return Optional.empty();
        }

        return Optional.of(new Draft(line, firstName, lastName, email,
                phone.isEmpty() ? null : phone, department, jobTitle, parsedDate,
                managerEmail.isEmpty() ? null : managerEmail));
    }

    private void require(String value, String field, int line,
                         List<ImportReport.RowError> errors) {
        if (value.isEmpty()) {
            errors.add(new ImportReport.RowError(line, field + " is required"));
        }
    }

    /**
     * Once herkesi yazar, SONRA yoneticileri baglar.
     *
     * Tek gecis yetmez: bir satirin yoneticisi dosyada DAHA ASAGIDA olabilir
     * ve o an henuz mevcut degildir. Ayni iki gecisli desen demo
     * tohumlayicisinda da var.
     *
     * Yonetici DAIMA veritabanindan aranir, ayrica bir bellek haritasindan
     * degil. Ilk gecis herkesi zaten yazdigi icin ayni dosyadaki yonetici de
     * oradan bulunur -- iki ayri yol tutmak, ikisinden birinin zamanla geride
     * kalmasi demekti.
     *
     * OLCULEREK bulundu: harita once vardi ve onu bozarak curutmeye
     * calistigimda test KIRILMADI, cunku veritabani yedegi zaten ayni cevabi
     * veriyordu. Kirilamayan bir yol, gereksiz bir yoldur.
     */
    private ImportReport apply(List<Draft> drafts, List<ImportReport.RowError> errors) {
        Map<String, Employee> saved = new HashMap<>();

        for (Draft draft : drafts) {
            Employee employee = new Employee(draft.firstName(), draft.lastName(), draft.email(),
                    draft.department(), draft.jobTitle(), draft.hireDate());
            employee.setPhone(draft.phone());

            saved.put(draft.email().toLowerCase(Locale.ROOT), employees.save(employee));
        }

        for (Draft draft : drafts) {
            if (draft.managerEmail() == null) {
                continue;
            }

            Employee manager = employees.findByEmail(draft.managerEmail()).orElse(null);

            if (manager == null) {
                errors.add(new ImportReport.RowError(draft.line(),
                        "No employee with email " + draft.managerEmail() + " to be the manager"));
            } else if (!manager.isActive()) {
                errors.add(new ImportReport.RowError(draft.line(),
                        "Manager " + draft.managerEmail() + " has left"));
            } else {
                saved.get(draft.email().toLowerCase(Locale.ROOT)).setManager(manager);
            }
        }

        // Yonetici cozumu ancak yazdiktan SONRA yapilabiliyor; burada cikan
        // hata transaction'i geri almak zorunda, yoksa yoneticisiz yarim bir
        // yukleme kalirdi.
        if (!errors.isEmpty()) {
            throw reject(errors);
        }

        log.info("Imported {} employee(s) from a CSV file", drafts.size());

        return ImportReport.accepted(drafts.size());
    }
}
