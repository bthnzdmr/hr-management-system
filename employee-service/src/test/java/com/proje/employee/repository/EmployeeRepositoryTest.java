package com.proje.employee.repository;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        // Test, veritabaninin onceki durumuna bagimli olmamali. users.employee_id
        // yabanci anahtari personel silmeyi engeller, bu yuzden baglanti once
        // koparilir; @DataJpaTest transaction icinde calisip geri alindigi icin
        // ne silme ne de koparma kalicidir.
        entityManager.createQuery("DELETE FROM LeaveRequest").executeUpdate();
        entityManager.createQuery("UPDATE User u SET u.employee = null").executeUpdate();
        employeeRepository.deleteAllInBatch();

        List<Department> departments = departmentRepository.findAll();

        for (int i = 0; i < 5; i++) {
            Employee employee = new Employee(
                    "Name" + i,
                    "Surname" + i,
                    "test" + i + "@example.com",
                    departments.get(i),
                    "Engineer",
                    LocalDate.of(2024, 1, 1));
            employeeRepository.save(employee);
        }

        // flush: bekleyen INSERT'leri veritabanina yaz.
        // clear: birinci seviye onbellegi bosalt ki sorgular gercekten veritabanina gitsin.
        entityManager.flush();
        entityManager.clear();

        statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("Without JOIN FETCH each department costs a separate query (N+1)")
    void issuesNPlusOneQueriesWithoutJoinFetch() {
        List<Employee> employees = employeeRepository.findAll();

        // Vekil nesneye dokunmak veritabanina gidilmesine sebep olur.
        employees.forEach(employee -> employee.getDepartment().getName());

        long queryCount = statistics.getPrepareStatementCount();

        assertThat(employees).hasSize(5);
        assertThat(queryCount).isEqualTo(6);   // 1 liste + 5 departman
    }

    @Test
    @DisplayName("JOIN FETCH loads departments in the same query")
    void issuesSingleQueryWithJoinFetch() {
        // Uretimin gercekten cagirdigi sorgu olculur. Onceden yalnizca
        // testlerde kullanilan ayri bir metot vardi ve olcum, kullanilmayan
        // sorguyu koruyordu.
        Page<Employee> page = employeeRepository.search(
                null, null, null, false, PageRequest.of(0, 10, Sort.by("lastName")));

        page.getContent().forEach(employee -> employee.getDepartment().getName());

        long queryCount = statistics.getPrepareStatementCount();

        assertThat(page.getContent()).hasSize(5);

        // Tek sorgu: departmanlar JOIN ile geldigi icin dongude veritabanina gidilmiyor.
        // Sayim sorgusu da atilmiyor -- ilk sayfada ve sonuc sayfa boyutundan az oldugu
        // icin Spring Data toplami zaten biliyor.
        assertThat(queryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Derived query finds a record by email")
    void findsRecordByEmail() {
        assertThat(employeeRepository.findByEmail("test0@example.com")).isPresent();
        assertThat(employeeRepository.findByEmail("missing@example.com")).isEmpty();
        assertThat(employeeRepository.existsByEmail("test1@example.com")).isTrue();
    }
}
