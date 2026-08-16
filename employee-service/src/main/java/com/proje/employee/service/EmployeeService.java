package com.proje.employee.service;

import com.proje.employee.audit.AuditAction;
import com.proje.employee.audit.Auditable;
import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.EmployeeUpdateRequest;
import com.proje.employee.dto.SalaryResponse;
import com.proje.employee.dto.SalaryUpdateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.TerminationReason;
import com.proje.employee.event.EmployeeEvent;
import com.proje.employee.event.EmployeeEventType;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.exception.DepartmentNotFoundException;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.exception.InactiveDepartmentException;
import com.proje.employee.exception.InactiveManagerException;
import com.proje.employee.exception.ManagerCycleException;
import com.proje.employee.exception.MissingTerminationReasonException;
import com.proje.employee.mapper.EmployeeMapper;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
// Locale.ROOT sart: Turkce locale'de "I".toLowerCase() "ı" uretir ve
// "ISMAIL" aramasi "ismail" kaydini bulamaz. Kucultme dile bagli olmamali.
import java.util.Locale;
import java.util.UUID;

@Service
public class EmployeeService {

    // Zincirde bozuk veri varsa sonsuz donguye girmemek icin ust sinir.
    private static final int MAX_HIERARCHY_DEPTH = 100;

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final EmployeeMapper employeeMapper;
    private final OutboxWriter outboxWriter;
    private final UserService userService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           DepartmentRepository departmentRepository,
                           EmployeeMapper employeeMapper,
                           OutboxWriter outboxWriter,
                           UserService userService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.employeeMapper = employeeMapper;
        this.outboxWriter = outboxWriter;
        this.userService = userService;
    }

    /**
     * @param search ad, soyad veya e-postada gecen metin; bos ise filtre yok
     * @param active true/false ile duruma gore suzer; null ise hepsi
     * @param scope cagiranin hangi satirlari gorebildigi
     */
    @Transactional(readOnly = true)
    public Page<EmployeeResponse> getAll(String search, Boolean active,
                                         AccessScope scope, Pageable pageable) {

        // Rolu var ama personel kaydina bagli degil: gorebilecegi hicbir satir yok.
        // Bos sayfa donmek dogru cevaptir; hata degil, sadece bos bir sonuc.
        if (scope.isEmpty()) {
            return Page.empty(pageable);
        }

        // Joker karakterler burada eklenir, sorguda degil: "%" karakterini
        // sorgu metnine gomup parametreyle birlestirmek okunmasi zor bir
        // ifade uretir ve LIKE deseni ile veriyi karistirir.
        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase(Locale.ROOT) + "%";

        return employeeRepository
                .search(pattern, active, scope.visibleEmployeeId(), scope.includesDirectReports(), pageable)
                .map(employeeMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id, AccessScope scope) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        // Kapsam disindaki kayit icin 403 DEGIL 404 donulur: "bu kayit var ama
        // goremezsin" demek, kaydin varligini sizdirir ve id deneyerek personel
        // sayisi ogrenilebilirdi.
        if (!canSee(employee, scope)) {
            throw new EmployeeNotFoundException(id);
        }

        return employeeMapper.toResponse(employee);
    }

    private boolean canSee(Employee employee, AccessScope scope) {
        if (scope.isUnrestricted()) {
            return true;
        }
        if (scope.isEmpty()) {
            return false;
        }
        if (scope.employeeId().equals(employee.getId())) {
            return true;
        }
        return scope.includesDirectReports()
                && employee.getManager() != null
                && scope.employeeId().equals(employee.getManager().getId());
    }

    @Transactional
    @Auditable(action = AuditAction.EMPLOYEE_CREATED, targetType = "EMPLOYEE")
    public EmployeeResponse create(EmployeeCreateRequest request) {
        // Kullaniciya anlamli mesaj donmek icin. Dogruluk garantisi bu kontrol
        // degil, veritabanindaki uk_employee_email kisitidir.
        if (employeeRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Department department = resolveDepartment(request.departmentId());

        Employee employee = new Employee(
                request.firstName(),
                request.lastName(),
                request.email(),
                department,
                request.jobTitle(),
                request.hireDate());

        employee.setPhone(request.phone());

        // Guncelleme ile AYNI yol kullanilir. Ayri bir arama yazildiginda
        // "pasif kisi yonetici olamaz" kurali yalnizca guncellemede geceriydi;
        // ayni kurali iki yere yazmak, birinin geride kalmasi demektir.
        employee.setManager(resolveManager(employee, request.managerId()));

        Employee saved = employeeRepository.save(employee);
        publish(EmployeeEventType.CREATED, saved);

        return employeeMapper.toResponse(saved);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if (!employee.getEmail().equals(request.email())
                && employeeRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Department department = resolveDepartment(request.departmentId());

        employee.setFirstName(request.firstName());
        employee.setLastName(request.lastName());
        employee.setEmail(request.email());
        employee.setPhone(request.phone());
        employee.setDepartment(department);
        employee.setJobTitle(request.jobTitle());
        employee.setHireDate(request.hireDate());
        employee.setManager(resolveManager(employee, request.managerId()));

        publish(EmployeeEventType.UPDATED, employee);

        // save() cagrilmadi: entity transaction icinde yonetiliyor, degisiklikler
        // commit sirasinda otomatik yazilir (dirty checking).
        return employeeMapper.toResponse(employee);
    }

    /**
     * Maas yalnizca bu iki metotla okunur ve yazilir.
     *
     * Genel guncelleme maasa hic dokunmaz; boylece maasi okuyamayan bir
     * istemcinin onu yanlislikla silmesi mumkun degildir. Yetki kontrolu uc
     * seviyesindedir (SecurityConfig), bu yuzden servis cagiranin rolunu bilmez.
     */
    // Sinirsiz kapsam (Ik) herkesinkini gorur; digerleri yalnizca kendi kaydini.
    // SYSTEM_ADMIN uc seviyesinde zaten disarida.
    @Transactional(readOnly = true)
    public SalaryResponse getSalary(Long id, AccessScope scope) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        // Olcut GENEL kapsam DEGIL, ucrete ozel eksendir. isUnrestricted()
        // sorulsaydi Ik veya sistem yoneticisi rolu tasiyan herkes butun
        // maaslari okurdu -- roller birlesince en genis yetki kazanirdi.
        if (!scope.includesAllSalaries() && !id.equals(scope.employeeId())) {
            throw new EmployeeNotFoundException(id);
        }

        return new SalaryResponse(employee.getId(), employee.getSalary());
    }

    @Transactional
    @Auditable(action = AuditAction.SALARY_CHANGED, targetType = "EMPLOYEE",
            includeArguments = false)
    public SalaryResponse updateSalary(Long id, SalaryUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        // Deger degismediyse olay uretilmez. PUT idempotent olmalidir; aksi
        // halde ayni tutarla yapilan her istek YENI bir eventId uretir ve
        // personel her seferinde bir "kaydiniz guncellendi" maili daha alir.
        // compareTo kullanilir: 95000 ile 95000.00 esittir ama equals degildir.
        BigDecimal current = employee.getSalary();
        if (current != null && current.compareTo(request.salary()) == 0) {
            return new SalaryResponse(employee.getId(), current);
        }

        employee.setSalary(request.salary());

        // Kayit degistigi icin olay yayinlanir. Olay maasi TASIMAZ; personel
        // yalnizca kaydinin guncellendigini ogrenir.
        publish(EmployeeEventType.UPDATED, employee);

        return new SalaryResponse(employee.getId(), employee.getSalary());
    }

    /**
     * Personeli pasiflestirir veya yeniden aktiflestirir.
     *
     * Tek uc iki yonu de yonetir ve dogasi geregi idempotenttir: ayni durumu
     * ikinci kez yazmak hicbir sey degistirmez ve olay uretmez. Aksi halde her
     * tekrar YENI bir eventId ureteceginden tuketicinin idempotency'si onu
     * ayiklayamaz ve personel her tiklamada bir mail daha alirdi.
     */
    @Transactional
    @Auditable(action = AuditAction.EMPLOYEE_STATUS_CHANGED, targetType = "EMPLOYEE")
    public EmployeeResponse changeStatus(Long id, boolean active, TerminationReason reason) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        if (employee.isActive() == active) {
            return employeeMapper.toResponse(employee);
        }

        if (active) {
            employee.reactivate();
        } else {
            // Sebep zorunlu: eksikse varsayilan atamak yerine istek reddedilir.
            // Uydurulmus bir sebep, devir oranini sessizce yanlis gosterirdi.
            if (reason == null) {
                throw new MissingTerminationReasonException();
            }
            // Tarihi SUNUCU koyar. Istemciye birakilsaydi gecmise donuk kayit
            // girilebilir ve devir orani istenildigi gibi sekillendirilebilirdi.
            employee.terminate(LocalDate.now(), reason);

            // JML'in "leaver" adimi. AYNI transaction icinde: hesap kapatma
            // basarisiz olursa personel ayrilisi da geri alinir. Olay uzerinden
            // asenkron yapilsaydi kisa da olsa "ayrilmis ama hala girebiliyor"
            // penceresi kalirdi -- guvenlik islemi nihai tutarliliga birakilmaz.
            userService.disableAccountOf(employee.getId());
        }

        publish(active ? EmployeeEventType.REACTIVATED : EmployeeEventType.DEACTIVATED, employee);

        return employeeMapper.toResponse(employee);
    }

    /**
     * Dogrudan bagli personel.
     *
     * Iki yerde kullanilir: detay ekraninda ekibi gostermek ve pasiflestirme
     * onayindan once "bu kisinin astlari var" uyarisini verebilmek. Yonetici
     * pasiflestiginde astlarin manager_id'si oldugu gibi kalir; kullanicinin
     * bunu BILEREK yapmasi gerekir.
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponse> getDirectReports(Long id, AccessScope scope) {
        Employee manager = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));

        // Astlarini gorebilmen icin once o kisiyi gorebiliyor olman gerekir.
        // Aksi halde kapsam disindaki birinin ekibi, id denenerek okunabilirdi.
        if (!canSee(manager, scope)) {
            throw new EmployeeNotFoundException(id);
        }

        // Kontrol DONEN SATIRLARA da uygulanir. Yalnizca yoneticiye bakmak
        // yetmiyordu: canSee bir ast icin true dondugunden, o astin ekibini
        // istemek iki seviye asagidaki kisileri getiriyordu -- ayni caginin
        // getById ile 404 aldigi kayitlari. Bir koleksiyon donuyorsan
        // kontrolu koleksiyonun HER ELEMANINA uygularsin.
        return employeeRepository.findByManagerIdOrderByLastNameAsc(id).stream()
                .filter(report -> canSee(report, scope))
                .map(employeeMapper::toResponse)
                .toList();
    }

    // Olay, is verisiyle ayni transaction icinde outbox tablosuna yazilir.
    // Tek veritabanina tek yazma oldugu icin ikisi ya birlikte kalici olur
    // ya birlikte geri alinir; broker bu noktada hic devrede degildir.
    private void publish(EmployeeEventType type, Employee employee) {
        outboxWriter.write(new EmployeeEvent(
                UUID.randomUUID().toString(),
                type,
                Instant.now(),
                employee.getId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getEmail(),
                employee.getDepartment().getName(),
                employee.getJobTitle()));
    }

    private Employee resolveManager(Employee employee, Long managerId) {
        if (managerId == null) {
            return null;
        }

        if (managerId.equals(employee.getId())) {
            throw new ManagerCycleException(employee.getId(), managerId);
        }

        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new EmployeeNotFoundException(managerId));

        // Pasif bir kisi yonetici olarak ATANAMAZ. Mevcut atamalar korunur:
        // bir yonetici pasiflestiginde astlarinin bagi kopmaz, ama yeni kimse
        // ona baglanamaz.
        if (!manager.isActive()) {
            throw new InactiveManagerException(managerId);
        }

        assertNoCycle(employee, manager);
        return manager;
    }

    /**
     * Departmani cozer ve PASIF olani reddeder.
     *
     * Tek yol: create ve update ayni metodu cagirir. Kural iki yere
     * yazilsaydi biri geride kalirdi -- projede bu tam olarak iki kez
     * yasandi (pasif yonetici kurali ve ayrilmis personele hesap acma).
     *
     * Mevcut atamalar korunur: bir departman kapandiginda icindeki
     * personelin baglantisi kopmaz. Engellenen sey YENI baglanti kurmaktir.
     */
    private Department resolveDepartment(Long departmentId) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DepartmentNotFoundException(departmentId));

        if (!department.isActive()) {
            throw new InactiveDepartmentException(departmentId);
        }

        return department;
    }

    // Yeni yoneticiden yukari dogru bakar. Yolda calisanin kendisine
    // rastlanirsa dongu olusuyor demektir. Veritabani CHECK kisiti bunu
    // goremez cunku birden fazla satirin gezilmesi gerekir.
    //
    // Zincir TEK SORGUDA cekilir. Onceki hali Java'da yuruyordu ve her
    // seviyede tembel vekili cozdugu icin seviye basina bir SELECT atiyordu:
    // bir YAZMA transaction'inin icinde, en kotu durumda 100 gidis donus.
    private void assertNoCycle(Employee employee, Employee newManager) {
        List<Long> ancestors =
                employeeRepository.findAncestorIds(newManager.getId(), MAX_HIERARCHY_DEPTH);

        if (ancestors.contains(employee.getId())) {
            throw new ManagerCycleException(employee.getId(), newManager.getId());
        }

        // Ust sinira ULASMAK basarili bir kontrol degildir. Sorgu derinlik
        // sigortasinda durur; dongu oradaysa zincir kisalmis gorunur ve
        // kontrol sessizce gecerdi. Gurultulu basarisiz ol.
        if (ancestors.size() >= MAX_HIERARCHY_DEPTH) {
            throw new IllegalStateException(
                    "Manager chain exceeded " + MAX_HIERARCHY_DEPTH
                            + " levels starting from employee " + newManager.getId());
        }
    }
}
