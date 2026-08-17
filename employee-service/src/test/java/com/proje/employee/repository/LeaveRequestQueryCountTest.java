package com.proje.employee.repository;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Izin listesinin sorgu sayisi.
 *
 * Cevap uc ayri iliskiyi okuyor: personel, karari veren ve KAYDI GIREN.
 * Sonuncusu sonradan eklendi ve JOIN FETCH'e alinmasaydi satir basina bir
 * sorgu daha atilirdi.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeaveRequestQueryCountTest {

    private static final int PAGE_SIZE = 10;

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        entityManager.createQuery("DELETE FROM LeaveRequest").executeUpdate();
        entityManager.createQuery("UPDATE User u SET u.employee = null").executeUpdate();
        userRepository.deleteAllInBatch();
        employeeRepository.deleteAllInBatch();

        Department department = departmentRepository.findAll().get(0);

        for (int i = 0; i < PAGE_SIZE; i++) {
            // Her kaydin AYRI bir yazari var. Tek yazarla kurulsaydi tembel
            // yukleme 10 degil 1 ek sorgu uretir ve N+1 gorunmez olurdu --
            // ilk yazdigim hal boyleydi ve test JOIN FETCH kaldirilinca bile
            // yesil kaliyordu.
            User author = userRepository.save(new User(
                    "recorder" + i + "@example.com", "hash", User.rolesOf(Role.HR_SPECIALIST)));

            Employee employee = employeeRepository.save(new Employee(
                    "Count", "Person" + i, "count" + i + "@example.com",
                    department, "Engineer", LocalDate.of(2024, 1, 1)));

            leaveRequestRepository.save(new LeaveRequest(employee, author, LeaveType.ANNUAL,
                    LocalDate.of(2035, 1, 1).plusDays(i * 10L),
                    LocalDate.of(2035, 1, 3).plusDays(i * 10L), null));
        }
        entityManager.flush();
        entityManager.clear();

        statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("Loading a page of leave requests stays at a constant query count")
    void listingLeaveDoesNotScaleWithPageSize() {
        var page = leaveRequestRepository.search(null, null, LeaveRequestRepository.BEGINNING_OF_TIME, LeaveRequestRepository.END_OF_TIME,
                PageRequest.of(0, PAGE_SIZE));

        // Iliskilere DOKUNULUR: cevap uretiminde hepsi okunuyor. Dokunmasaydik
        // tembel vekiller hic baslatilmaz ve test bos yere gecerdi.
        long touched = page.getContent().stream()
                .mapToLong(l -> l.getEmployee().getLastName().length()
                        + l.getCreatedBy().getEmail().length())
                .sum();
        assertThat(touched).isPositive();

        assertThat(statistics.getPrepareStatementCount())
                .as("a page of %d leave requests must not cost one query per row", PAGE_SIZE)
                // Olculdu: 3 (kayitlar + toplam sayim + EAGER roller). Satir
                // sayisiyla BUYUMEZ; JOIN FETCH kaldirilirsa buyur.
                .isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("Reading one request loads its relations in the same query")
    void readingOneRequestStaysSingleQuery() {
        Long id = leaveRequestRepository.findAll().get(0).getId();
        // Kimlik onceki sorgudan geliyor; olcum YALNIZCA okuma icin baslar.
        entityManager.clear();
        statistics.clear();

        LeaveRequest found = leaveRequestRepository.findByIdWithEmployee(id).orElseThrow();
        found.getCreatedBy().getEmail();
        found.getEmployee().getLastName();

        // Olculdu: IKI sorgu. Biri kaydin kendisi, digeri User.roles -- o
        // koleksiyon EAGER ve kaydi girenle birlikte yukleniyor. JOIN FETCH
        // kaldirilsaydi sayi buyurdu.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
