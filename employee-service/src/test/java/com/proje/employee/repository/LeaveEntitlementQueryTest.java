package com.proje.employee.repository;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveEntitlement;
import com.proje.employee.entity.TerminationReason;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tahakkuk sorgusunun GERCEK veritabanina karsi testi.
 *
 * <p>Servis testi bu sorguyu taklit ediyor ve orada sinanan sey servisin
 * KARARIDIR (merdiven, devir, isaret). Ama taklit edilen bir sorgu SQL'i
 * kanitlamaz -- ve bu OLCULDU: JPQL'den {@code NOT EXISTS} silindiginde
 * butun servis testleri YESIL kaldi.
 *
 * <p>Oysa tahakkuk isinin en kritik ozelligi tam da orada yasiyor: is her gun
 * calisiyor ve var olan satiri getirseydi Ik'nin elle yaptigi duzeltme ertesi
 * gun SESSIZCE uzerine yazilirdi.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeaveEntitlementQueryTest {

    private static final int YEAR = 2033;

    @Autowired
    private LeaveEntitlementRepository entitlements;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private DepartmentRepository departments;

    @Autowired
    private EntityManager entityManager;

    private Department department;

    @BeforeEach
    void setUp() {
        // Onceki duruma bagimli olmamak icin. @DataJpaTest transaction icinde
        // calisip geri aldigi icin silme kalici degil.
        entityManager.createQuery("DELETE FROM LeaveEntitlement").executeUpdate();
        entityManager.createQuery("DELETE FROM LeaveRequest").executeUpdate();
        entityManager.createQuery("UPDATE User u SET u.employee = null").executeUpdate();
        employees.deleteAllInBatch();

        department = departments.findAll().stream().findFirst()
                .orElseGet(() -> departments.save(new Department("Entitlements")));
    }

    private Employee person(String name) {
        return employees.save(new Employee(name, "Test", name + "@example.com",
                department, "Engineer", LocalDate.of(2028, 3, 1)));
    }

    private void grant(Employee employee, int year) {
        entitlements.save(new LeaveEntitlement(employee, year, 14, 0, "Already decided"));
    }

    private List<Employee> awaitingAccrual() {
        return entitlements.findActiveWithoutEntitlement(YEAR);
    }

    @Test
    @DisplayName("Somebody with no entitlement for the year is awaiting accrual")
    void anEmployeeWithoutARowIsIncluded() {
        Employee grace = person("grace");

        assertThat(awaitingAccrual()).extracting(Employee::getId).containsExactly(grace.getId());
    }

    @Test
    @DisplayName("Somebody who already has a row for the year is left alone")
    void anEmployeeWithARowIsExcluded() {
        // TAHAKKUK ISININ EN KRITIK OZELLIGI. Bu satir Ik'nin elle verdigi hak
        // olabilir; is ona dokunursa duzeltme ertesi gun geri alinir.
        Employee grace = person("grace");
        grant(grace, YEAR);

        assertThat(awaitingAccrual()).isEmpty();
    }

    @Test
    @DisplayName("A row for another year does not count as this year's")
    void aRowForAnotherYearDoesNotCount() {
        // Yil kosulu dusseydi ilk tahakkuktan sonra HICBIR yil daha
        // isleyemezdi: herkesin gecen yildan satiri var.
        Employee grace = person("grace");
        grant(grace, YEAR - 1);

        assertThat(awaitingAccrual()).extracting(Employee::getId).containsExactly(grace.getId());
    }

    @Test
    @DisplayName("Another person's row does not cover this person")
    void somebodyElsesRowDoesNotCover() {
        // Personel eslesmesi dusseydi TEK bir satir butun kadroyu ortbas eder
        // ve kimseye hak tahakkuk etmezdi.
        Employee grace = person("grace");
        Employee ada = person("ada");
        grant(ada, YEAR);

        assertThat(awaitingAccrual()).extracting(Employee::getId).containsExactly(grace.getId());
    }

    @Test
    @DisplayName("Somebody who has left does not accrue new entitlement")
    void aTerminatedEmployeeIsExcluded() {
        // Ayrilan birine yeni yil hakki tahakkuk ettirmek, olmayan bir alacak
        // uretmek olurdu.
        Employee grace = person("grace");
        grace.terminate(LocalDate.now(), TerminationReason.RESIGNED);
        employees.save(grace);

        assertThat(awaitingAccrual()).isEmpty();
    }

    @Test
    @DisplayName("Only the people still missing a row come back on a second pass")
    void asecondPassReturnsOnlyWhatIsStillMissing() {
        // Isin gunluk calismasi bunu gerektiriyor: ilk gecis herkesi yazar,
        // ertesi gun liste BOS doner ve hicbir sey yazilmaz.
        Employee grace = person("grace");
        Employee ada = person("ada");

        assertThat(awaitingAccrual()).hasSize(2);

        grant(grace, YEAR);

        assertThat(awaitingAccrual()).extracting(Employee::getId).containsExactly(ada.getId());
    }
}
