package com.proje.employee.service;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dislama kisitinin GERCEKTEN eszamanli iki istekte tuttugu.
 *
 * <p>{@code LeaveOverlapConstraintTest} kisitin dogru KURULDUGUNU siniyor:
 * pes pese iki yazma, ikincisi reddediliyor. Ama o, yarisin kendisini
 * uretmez -- tek iplikte ikinci yazma zaten ilkini GORUR. Kisitin var olma
 * sebebi ise tam olarak ikisinin BIRBIRINI GOREMEDIGI andir: klasik
 * check-then-act'te iki istek de "cakisma yok" der ve ikisi de yazar.
 *
 * <p><b>Neden NOT_SUPPORTED?</b> {@code @DataJpaTest} test metodunu bir
 * transaction'a sarar ve sonunda geri alir. Boyle bir kurulumda iki iplik
 * carpisamaz: yardimci iplikler testin COMMIT EDILMEMIS satirlarini hic
 * gormez. Yaris ancak her iplik KENDI transaction'ini acip commit ettiginde
 * olusur, dolayisiyla testin kendisi transaction disinda kalmali -- ve
 * temizligi elle yapmali.
 *
 * <p>PostgreSQL'de ikinci INSERT hemen patlamaz: index girisi kilitli
 * oldugu icin birincinin sonucunu BEKLER, birinci commit edince
 * {@code 23P01} ile duser. Yani sinanan sey yalnizca kisit degil, kilit
 * davranisidir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeaveOverlapRaceTest {

    /** Kendi satirlarimizi bulup silebilmek icin ayirt edici. */
    private static final String MARKER = "race.overlap@example.com";

    private static final LocalDate START = LocalDate.of(2031, 4, 10);
    private static final LocalDate END = LocalDate.of(2031, 4, 15);

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private UserRepository users;

    @Autowired
    private DepartmentRepository departments;

    private Employee employee;
    private User author;

    @BeforeEach
    void setUp() {
        cleanUp();

        Department department = departments.findAllByOrderByNameAsc().stream().findFirst()
                .orElseGet(() -> departments.saveAndFlush(new Department("Race test dept")));

        employee = employees.saveAndFlush(new Employee("Race", "Tester",
                MARKER, department, "Engineer", LocalDate.now()));

        author = users.saveAndFlush(new User("race.author@example.com", "hash",
                Set.of(Role.HR_SPECIALIST)));
    }

    /**
     * Hicbir sey geri ALINMAZ, bu yuzden temizlik elle.
     *
     * Sira onemli: izin satirlari personele yabanci anahtarla bagli.
     */
    @AfterEach
    void cleanUp() {
        employees.findByEmail(MARKER).ifPresent(existing -> {
            leaveRequests.deleteAllInBatch(leaveOf(existing.getId()));
            employees.delete(existing);
        });
        users.findByEmail("race.author@example.com").ifPresent(users::delete);
    }

    /**
     * Bu personelin izinleri.
     *
     * Uretim repository'sine bir bulucu EKLENMEDI: yalnizca test icin
     * genisleyen bir arayuz, uretim kodunda kullanilmayan bir metot
     * birakirdi. Kurgu kucuk, suzme Java'da yapiliyor.
     */
    private List<LeaveRequest> leaveOf(Long employeeId) {
        return leaveRequests.findAll().stream()
                .filter(leave -> leave.getEmployee().getId().equals(employeeId))
                .toList();
    }

    @Test
    @DisplayName("Two concurrent requests for the same dates: exactly one survives")
    void concurrentOverlappingRequestsCollide() throws Exception {
        int accepted = raceTwoInserts(START, END);

        assertThat(accepted)
                .describedAs("the exclusion constraint must let exactly one writer through")
                .isEqualTo(1);

        assertThat(leaveOf(employee.getId()))
                .describedAs("only the winning row may reach the table")
                .hasSize(1);
    }

    @Test
    @DisplayName("Two concurrent requests for separate dates both survive")
    void concurrentNonOverlappingRequestsBothSucceed() {
        // KURGUNUN KENDISINI dogrular: yukaridaki testin 1 dondurmesi,
        // kurulumun eszamanlilik uretemediginden DEGIL gercekten
        // carpistiklarindan. Ayni kurulum cakismayan tarihlerde 2 donmeli --
        // donmeseydi ilk test her kosulda gecerdi ve hicbir sey kanitlamazdi.
        int accepted = raceTwoInsertsAt(START, END, START.plusMonths(2), END.plusMonths(2));

        assertThat(accepted)
                .describedAs("non-overlapping writers must not block each other")
                .isEqualTo(2);
    }

    private int raceTwoInserts(LocalDate start, LocalDate end) throws Exception {
        return raceTwoInsertsAt(start, end, start, end);
    }

    /**
     * Iki ipligi ayni ana getirip birer izin yazdirir.
     *
     * @return kabul edilen yazma sayisi
     */
    private int raceTwoInsertsAt(LocalDate firstStart, LocalDate firstEnd,
                                 LocalDate secondStart, LocalDate secondEnd) {
        // Bariyer olmadan ilk iplik ikincisi baslamadan bitebilir ve yaris
        // hic olusmaz -- test yine gecer ama olcmek istedigi durumu
        // uretmemis olur.
        CyclicBarrier barrier = new CyclicBarrier(2);

        Callable<Boolean> first = attempt(barrier, firstStart, firstEnd);
        Callable<Boolean> second = attempt(barrier, secondStart, secondEnd);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results = pool.invokeAll(List.of(first, second), 30, TimeUnit.SECONDS);

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            return accepted;

        } catch (Exception e) {
            throw new IllegalStateException("the race could not be run", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Boolean> attempt(CyclicBarrier barrier, LocalDate start, LocalDate end) {
        return () -> {
            barrier.await(15, TimeUnit.SECONDS);
            try {
                // saveAndFlush kendi transaction'ini acar ve commit eder:
                // test transaction disinda oldugu icin bu cagri gercekten
                // veritabanina yazar.
                leaveRequests.saveAndFlush(
                        new LeaveRequest(employee, author, LeaveType.ANNUAL, start, end, null));
                return true;
            } catch (DataIntegrityViolationException expected) {
                // 23P01 -- dislama ihlali. Kaybeden taraf, kazananin satirini
                // GORMUS demektir.
                return false;
            } catch (ConcurrencyFailureException expected) {
                // 40P01 -- deadlock. OLCULEREK bulundu ve tesaduf degil:
                // ikisi de once satirini yazip SONRA index'i kontrol ediyor,
                // dolayisiyla her biri digerinin commit'ini bekler ve
                // PostgreSQL dongusu kirmak icin birini oldurur.
                //
                // Spring bunu DataIntegrityViolationException'a DEGIL
                // CannotAcquireLockException'a cevirir -- ikisi kardes,
                // biri digerinin alt sinifi degil. Servisin cakisma
                // yakalayicisi bu yuzden isliyordu ve kullanici 500
                // goruyordu; GlobalExceptionHandler artik 409 donduruyor.
                return false;
            }
        };
    }
}
