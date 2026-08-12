package com.proje.employee.repository;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.TerminationReason;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toplama sorgulari gercek veritabanina karsi calisir.
 *
 * Bu sorgularin degeri tam da PostgreSQL'in verdigi cevapta: FILTER,
 * generate_series ve date_trunc sahte bir depoyla sinanamaz.
 *
 * "docker compose up -d" gerektirir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DashboardRepositoryTest {

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    private Department sales;

    @BeforeEach
    void setUp() {
        // Onceki durumdan bagimsiz baslamak icin. Departmanlar migration ile
        // gelir ve silinmez; yalnizca personel temizlenir.
        // users.employee_id yabanci anahtari personel silmeyi engeller. Baglanti
        // once koparilir; @DataJpaTest transaction icinde calisip geri
        // alindigi icin gercek hesaplar etkilenmez.
        entityManager.createQuery("UPDATE User u SET u.employee = null").executeUpdate();
        employeeRepository.deleteAllInBatch();
        sales = departmentRepository.findByName("Sales").orElseThrow();
    }

    private Employee employee(String email, LocalDate hiredOn) {
        Employee employee = new Employee("First", "Last", email, sales,
                "Engineer", hiredOn);
        return employeeRepository.save(employee);
    }

    @Test
    @DisplayName("Counts active, inactive and recent hires in a single pass")
    void countsHeadcount() {
        employee("a@example.com", LocalDate.now().minusDays(10));
        employee("b@example.com", LocalDate.now().minusDays(60));
        employee("c@example.com", LocalDate.now().minusYears(3));

        Employee leaver = employee("d@example.com", LocalDate.now().minusYears(2));
        leaver.terminate(LocalDate.now().minusMonths(2), TerminationReason.RESIGNED);
        employeeRepository.save(leaver);
        entityManager.flush();

        DashboardRepository.Headcount headcount = dashboardRepository.headcount();

        assertThat(headcount.getActiveCount()).isEqualTo(3);
        assertThat(headcount.getInactiveCount()).isEqualTo(1);
        assertThat(headcount.getHiredLast30Days()).isEqualTo(1);
        // 90 gun 30 gunu KAPSAR: son 30 gunde girenler burada da sayilir.
        assertThat(headcount.getHiredLast90Days()).isEqualTo(2);
        assertThat(headcount.getLeftLast12Months()).isEqualTo(1);
    }

    @Test
    @DisplayName("Lists departments that have nobody in them")
    void includesEmptyDepartments() {
        // LEFT JOIN olmasaydi bos departman sonuctan tamamen duserdi; oysa
        // bos bir departman panelin gostermesi gereken bir bulgudur.
        employee("a@example.com", LocalDate.now());
        entityManager.flush();

        List<DashboardRepository.DepartmentHeadcount> rows =
                dashboardRepository.headcountByDepartment();

        assertThat(rows).hasSizeGreaterThanOrEqualTo(5);
        assertThat(rows.stream().anyMatch(row -> row.getActiveCount() == 0)).isTrue();
        assertThat(dashboardRepository.countEmptyDepartments()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Returns twelve months even when nobody left in some of them")
    void fillsMonthsWithoutLeavers() {
        // "Mart yok" ile "Mart'ta kimse ayrilmadi" farkli seylerdir; ikincisi
        // grafikte bir bosluk degil, sifir yuksekliginde bir sutundur.
        Employee leaver = employee("d@example.com", LocalDate.now().minusYears(2));
        leaver.terminate(LocalDate.now().minusMonths(1), TerminationReason.DISMISSED);
        employeeRepository.save(leaver);
        entityManager.flush();

        List<DashboardRepository.MonthlyTurnover> months = dashboardRepository.turnoverByMonth();

        assertThat(months).hasSize(12);
        assertThat(months.stream().mapToLong(DashboardRepository.MonthlyTurnover::getLeaverCount).sum())
                .isEqualTo(1);

        String expected = YearMonth.from(LocalDate.now().minusMonths(1)).toString();
        assertThat(months.stream()
                .filter(month -> month.getMonth().equals(expected))
                .findFirst().orElseThrow().getLeaverCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Groups leavers by the reason they gave")
    void groupsTerminationReasons() {
        Employee first = employee("a@example.com", LocalDate.now().minusYears(2));
        first.terminate(LocalDate.now().minusMonths(1), TerminationReason.RESIGNED);
        Employee second = employee("b@example.com", LocalDate.now().minusYears(2));
        second.terminate(LocalDate.now().minusMonths(2), TerminationReason.RESIGNED);
        Employee third = employee("c@example.com", LocalDate.now().minusYears(2));
        third.terminate(LocalDate.now().minusMonths(3), TerminationReason.RETIRED);
        employeeRepository.saveAll(List.of(first, second, third));
        entityManager.flush();

        List<DashboardRepository.ReasonCount> reasons = dashboardRepository.terminationReasons();

        assertThat(reasons).hasSize(2);
        assertThat(reasons.get(0).getReason()).isEqualTo("RESIGNED");
        assertThat(reasons.get(0).getLeaverCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Measures how many people each manager carries")
    void measuresSpanOfControl() {
        Employee boss = employee("boss@example.com", LocalDate.now().minusYears(3));
        Employee lead = employee("lead@example.com", LocalDate.now().minusYears(2));

        for (int i = 0; i < 3; i++) {
            Employee report = employee("r" + i + "@example.com", LocalDate.now().minusYears(1));
            report.setManager(boss);
            employeeRepository.save(report);
        }
        Employee single = employee("s@example.com", LocalDate.now().minusYears(1));
        single.setManager(lead);
        employeeRepository.save(single);
        entityManager.flush();

        DashboardRepository.SpanOfControl span = dashboardRepository.spanOfControl();

        assertThat(span.getManagerCount()).isEqualTo(2);
        assertThat(span.getLargestTeamSize()).isEqualTo(3);
        assertThat(span.getAverageDirectReports()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Reports zero instead of failing when there is no data at all")
    void survivesAnEmptyTable() {
        // Bos sistemde avg() NULL doner; coalesce olmasaydi cevap null tasir
        // ve arayuzde "NaN" gorunurdu.
        DashboardRepository.SpanOfControl span = dashboardRepository.spanOfControl();

        assertThat(span.getManagerCount()).isZero();
        assertThat(span.getAverageDirectReports()).isZero();
        assertThat(dashboardRepository.countActiveWithoutManager()).isZero();
    }

    @Test
    @DisplayName("Counts active people who have no manager assigned")
    void countsPeopleWithoutManager() {
        employee("a@example.com", LocalDate.now());
        Employee managed = employee("b@example.com", LocalDate.now());
        managed.setManager(employeeRepository.findByEmail("a@example.com").orElseThrow());
        employeeRepository.save(managed);
        entityManager.flush();

        assertThat(dashboardRepository.countActiveWithoutManager()).isEqualTo(1);
    }
}
