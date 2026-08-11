package com.proje.employee.mapper;

import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(EmployeeMapper.class)
class EmployeeMapperTest {

    @Autowired
    private EmployeeMapper mapper;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics;
    private Long managerId;

    @BeforeEach
    void setUp() {
        // Test, veritabaninin onceki durumuna bagimli olmamali.
        // @DataJpaTest transaction icinde calisip geri aldigi icin bu silme kalici degil.
        employeeRepository.deleteAllInBatch();

        Department department = departmentRepository.findAll().get(0);

        Employee manager = new Employee("Grace", "Hopper", "grace@example.com",
                department, "Engineering Manager", LocalDate.of(2020, 1, 1));
        employeeRepository.save(manager);
        managerId = manager.getId();

        Employee subordinate = new Employee("Ada", "Lovelace", "ada@example.com",
                department, "Software Engineer", LocalDate.of(2024, 1, 15));
        subordinate.setManager(manager);
        employeeRepository.save(subordinate);

        entityManager.flush();
        entityManager.clear();

        statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("Reading the id of a lazy proxy does not trigger an extra query")
    void readingProxyIdIssuesNoExtraQuery() {
        List<Employee> employees = employeeRepository
                .findAllWithDepartment(PageRequest.of(0, 10))
                .getContent();

        List<EmployeeResponse> responses = employees.stream()
                .map(mapper::toResponse)
                .toList();

        assertThat(responses).hasSize(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Reading the id of a manager outside the page issues no extra query")
    void readingProxyIdOutsidePageIssuesNoExtraQuery() {
        // Onceki test aslinda PROXY'yi sinamiyor: yonetici de ayni sayfada
        // dondugu icin getManager() zaten yuklenmis bir nesneye cozuluyor.
        // Burada sayfa boyutu 1: yonetici sonuc kumesinin DISINDA kaliyor ve
        // getManager() gercek bir HibernateProxy donuyor.
        // Sirala: lastName asc -> Hopper (yonetici), Lovelace (calisan).
        List<Employee> secondPage = employeeRepository
                .findAllWithDepartment(PageRequest.of(1, 1, Sort.by("lastName")))
                .getContent();

        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getLastName()).isEqualTo("Lovelace");

        long queriesBeforeMapping = statistics.getPrepareStatementCount();

        EmployeeResponse response = mapper.toResponse(secondPage.get(0));

        assertThat(response.managerId()).isEqualTo(managerId);
        assertThat(statistics.getPrepareStatementCount())
                .as("mapping a proxied manager must not hit the database")
                .isEqualTo(queriesBeforeMapping);
    }

    @Test
    @DisplayName("managerId is null when the employee has no manager")
    void mapsNullManagerId() {
        EmployeeResponse response = mapper.toResponse(
                employeeRepository.findByEmail("grace@example.com").orElseThrow());

        assertThat(response.managerId()).isNull();
        assertThat(response.departmentName()).isNotBlank();
    }

    @Test
    @DisplayName("managerId is populated when the employee has a manager")
    void mapsManagerId() {
        EmployeeResponse response = mapper.toResponse(
                employeeRepository.findByEmail("ada@example.com").orElseThrow());

        assertThat(response.managerId()).isEqualTo(managerId);
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.lastName()).isEqualTo("Lovelace");
    }
}
