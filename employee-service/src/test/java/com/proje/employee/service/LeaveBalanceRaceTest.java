package com.proje.employee.service;

import com.proje.employee.dto.LeaveRequestCreateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveEntitlement;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveEntitlementRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
 * Izin hakkinin eszamanli iki talepte asilamadigi.
 *
 * <p>Tarih cakismasini veritabani kisiti engelliyor ({@code EXCLUDE}) ama
 * "toplam gun" bir SATIR kisiti olarak ifade edilemez: kisit tek bir satira
 * bakar, bakiye ise satirlarin TOPLAMINA. Dolayisiyla bu degismezin tek
 * bekcisi servis katmanidir ve orada klasik bir <b>check-then-act</b> vardi:
 * iki talep de bakiyeyi okur, ikisi de "yeter" gorur, ikisi de yazar.
 *
 * <p>Kurgu bilerek CAKISMAYAN tarihler kullaniyor -- cakisan tarihlerde
 * ikinci talep zaten {@code EXCLUDE} kisitina takilirdi ve test, olcmek
 * istedigi seyi degil o kisiti olcerdi.
 *
 * <p>{@code @SpringBootTest} gerekli: sinanan mantik SERVIS katmaninda ve
 * {@code @DataJpaTest} onu hic yuklemez. {@code NOT_SUPPORTED} ise sart --
 * geri alinan bir test transaction'inda iki iplik carpisamaz, cunku
 * yardimci iplikler commit edilmemis satirlari hic gormez.
 */
@SpringBootTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class LeaveBalanceRaceTest {

    private static final String MARKER = "balance.race@example.com";
    private static final String AUTHOR = "balance.race.author@example.com";

    private static final int YEAR = 2034;
    private static final int ENTITLED_DAYS = 20;
    /** Iki tanesi hakki ASAR (30 > 20), tek tanesi sigar. */
    private static final int DAYS_PER_REQUEST = 15;

    @Autowired
    private LeaveRequestService leaveRequests;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private LeaveEntitlementRepository entitlements;

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
                .orElseGet(() -> departments.saveAndFlush(new Department("Balance race dept")));

        employee = employees.saveAndFlush(new Employee("Balance", "Racer",
                MARKER, department, "Engineer", LocalDate.of(YEAR - 5, 1, 1)));

        // Hak ACIKCA yaziliyor: yapilandirmadaki varsayilana guvenseydi test,
        // o ayar degistigi gun sessizce baska bir seyi olcmeye baslardi.
        entitlements.saveAndFlush(new LeaveEntitlement(employee, YEAR, ENTITLED_DAYS, 0,
                "balance race fixture"));

        author = users.saveAndFlush(new User(AUTHOR, "hash", Set.of(Role.HR_SPECIALIST)));
    }

    /** Hicbir sey geri ALINMAZ; temizlik elle ve yabanci anahtar sirasiyla. */
    @AfterEach
    void cleanUp() {
        employees.findByEmail(MARKER).ifPresent(existing -> {
            leaveRequestRepository.deleteAllInBatch(leaveOf(existing.getId()));
            entitlements.findByEmployeeIdAndYear(existing.getId(), YEAR)
                    .ifPresent(entitlements::delete);
            employees.delete(existing);
        });
        users.findByEmail(AUTHOR).ifPresent(users::delete);
    }

    @Test
    @DisplayName("Two concurrent requests cannot together exceed the annual entitlement")
    void concurrentRequestsCannotOverdrawTheBalance() {
        // 20 gunluk hak, her biri 15 gun isteyen IKI talep. Dogru davranis:
        // biri gecer, digeri "yeterli izin yok" der. Kontrol ile yazma
        // arasindaki pencere aciksa IKISI DE gecer ve kisi 30 gun izinli
        // gorunur -- kimsenin vermedigi 10 gun.
        int accepted = raceTwoRequests();

        assertThat(accepted)
                .describedAs("only one request may fit inside a %d day entitlement",
                        ENTITLED_DAYS)
                .isEqualTo(1);

        int booked = leaveOf(employee.getId()).stream()
                .mapToInt(leave -> (int) leave.getStartDate()
                        .datesUntil(leave.getEndDate().plusDays(1)).count())
                .sum();

        assertThat(booked)
                .describedAs("booked days must never exceed the entitlement")
                .isLessThanOrEqualTo(ENTITLED_DAYS);
    }

    private int raceTwoRequests() {
        CyclicBarrier barrier = new CyclicBarrier(2);

        // Tarihler CAKISMIYOR: ikisi de Haziran'da ama farkli araliklarda.
        // Cakissalardi EXCLUDE kisiti devreye girer ve bakiye kontrolu hic
        // sinanmamis olurdu.
        Callable<Boolean> first = attempt(barrier, LocalDate.of(YEAR, 6, 1));
        Callable<Boolean> second = attempt(barrier, LocalDate.of(YEAR, 8, 1));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> results =
                    pool.invokeAll(List.of(first, second), 30, TimeUnit.SECONDS);

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

    private Callable<Boolean> attempt(CyclicBarrier barrier, LocalDate start) {
        return () -> {
            LeaveRequestCreateRequest request = new LeaveRequestCreateRequest(
                    employee.getId(), LeaveType.ANNUAL,
                    start, start.plusDays(DAYS_PER_REQUEST - 1L), "balance race");

            barrier.await(15, TimeUnit.SECONDS);
            try {
                leaveRequests.create(request, author, AccessScope.forUser(author));
                return true;
            } catch (RuntimeException rejected) {
                // Bakiye yetmedi ya da yazma catismasi oldu: ikisi de
                // "kaybettim" demektir ve ikisi de MESRU.
                return false;
            }
        };
    }

    private List<LeaveRequest> leaveOf(Long employeeId) {
        return leaveRequestRepository.findAll().stream()
                .filter(leave -> leave.getEmployee().getId().equals(employeeId))
                .toList();
    }
}
