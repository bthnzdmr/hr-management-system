package com.proje.employee.repository;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ozyinelemeli CTE'nin GERCEK veritabanina karsi testi.
 *
 * <p>Servis testleri {@code findAncestorIds}'i taklit ediyor ve bu dogru: orada
 * sinanan sey servisin KARARIDIR (zincirde kendisi varsa reddet). Ama taklit
 * edilen bir sorgu, SQL'in dogru oldugunu hicbir sekilde kanitlamaz --
 * {@code WITH RECURSIVE} yanlis yazilmis olsa bile o testler yesil kalirdi.
 *
 * <p>Burada sinanan sey sorgunun kendisidir: zinciri dogru yonde mi yuruyor,
 * derinlik sigortasi gercekten duruyor mu, ve bir DONGU varsa sonsuza kadar
 * calisip sunucuyu tuketiyor mu.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmployeeAncestorQueryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    private Department department;

    @BeforeEach
    void setUp() {
        // Test onceki duruma bagimli olmamali. @DataJpaTest transaction icinde
        // calisip geri aldigi icin bu silme kalici degil.
        entityManager.createQuery("DELETE FROM LeaveRequest").executeUpdate();
        entityManager.createQuery("UPDATE User u SET u.employee = null").executeUpdate();
        employeeRepository.deleteAllInBatch();

        department = departmentRepository.findAll().stream().findFirst()
                .orElseGet(() -> departmentRepository.save(new Department("Ancestors")));
    }

    private Employee person(String email, Employee manager) {
        Employee employee = new Employee("A", email, email + "@example.com",
                department, "Engineer", LocalDate.now().minusYears(1));
        employee.setManager(manager);
        return employeeRepository.saveAndFlush(employee);
    }

    @Test
    @DisplayName("Walks the chain upwards and stops at the top")
    void returnsTheWholeChain() {
        Employee top = person("top", null);
        Employee middle = person("middle", top);
        Employee bottom = person("bottom", middle);

        List<Long> ancestors = employeeRepository.findAncestorIds(bottom.getId(), 100);

        // Baslangic satiri da dahildir: zincir kendisiyle baslar.
        assertThat(ancestors).containsExactly(bottom.getId(), middle.getId(), top.getId());
    }

    @Test
    @DisplayName("Returns only the employee when they report to nobody")
    void handlesEmployeeWithoutManager() {
        Employee alone = person("alone", null);

        assertThat(employeeRepository.findAncestorIds(alone.getId(), 100))
                .containsExactly(alone.getId());
    }

    @Test
    @DisplayName("Stops at the depth guard instead of returning the whole chain")
    void respectsTheDepthGuard() {
        Employee current = person("root", null);
        for (int i = 0; i < 8; i++) {
            current = person("level" + i, current);
        }

        // Zincir 9 kisi; sigorta 4'te durmali.
        assertThat(employeeRepository.findAncestorIds(current.getId(), 4)).hasSize(4);
    }

    @Test
    @DisplayName("Terminates on a cyclic chain instead of running forever")
    void terminatesOnCycle() {
        // ASIL SIGORTANIN SEBEBI BU. Veritabani CHECK kisiti yalnizca
        // "kendi kendinin yoneticisi" durumunu yakalar; A -> B -> A dolayli
        // dongusu veriye girebilir. Sigorta olmasaydi ozyineleme SONSUZA
        // KADAR calisir ve sorgu sunucuyu tuketirdi.
        Employee a = person("cyclea", null);
        Employee b = person("cycleb", a);

        // Donguyu VERITABANI SEVIYESINDE kur: servis katmani bunu reddeder,
        // ama veri baska bir yoldan (elle duzeltme, ice aktarma) bozulabilir.
        entityManager.createNativeQuery("UPDATE employee SET manager_id = :b WHERE id = :a")
                .setParameter("b", b.getId())
                .setParameter("a", a.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        List<Long> ancestors = employeeRepository.findAncestorIds(a.getId(), 10);

        // Sonlaniyor ve sigorta kadar satir donuyor: servis bunu "zincir cok
        // derin" diye yorumlayip gurultulu basarisiz oluyor.
        assertThat(ancestors).hasSize(10);
    }
}
