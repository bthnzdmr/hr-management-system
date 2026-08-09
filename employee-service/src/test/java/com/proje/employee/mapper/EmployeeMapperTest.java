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
    private Long yoneticiId;

    @BeforeEach
    void setUp() {
        // Test, veritabaninin onceki durumuna bagimli olmamali.
        // @DataJpaTest transaction icinde calisip geri aldigi icin bu silme kalici degil.
        employeeRepository.deleteAllInBatch();

        Department department = departmentRepository.findAll().get(0);

        Employee yonetici = new Employee("Grace", "Hopper", "grace@example.com",
                department, "Engineering Manager", LocalDate.of(2020, 1, 1));
        employeeRepository.save(yonetici);
        yoneticiId = yonetici.getId();

        Employee ast = new Employee("Ada", "Lovelace", "ada@example.com",
                department, "Software Engineer", LocalDate.of(2024, 1, 15));
        ast.setManager(yonetici);
        employeeRepository.save(ast);

        entityManager.flush();
        entityManager.clear();

        statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
    }

    @Test
    @DisplayName("Vekil yoneticinin id'sini okumak ek sorgu atmaz")
    void vekilIdOkumakSorguAtmaz() {
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
    @DisplayName("Yoneticisi olmayan kayitta managerId null doner")
    void yoneticisiOlmayanKayit() {
        EmployeeResponse response = mapper.toResponse(
                employeeRepository.findByEmail("grace@example.com").orElseThrow());

        assertThat(response.managerId()).isNull();
        assertThat(response.departmentName()).isNotBlank();
    }

    @Test
    @DisplayName("Yoneticisi olan kayitta managerId dolu doner")
    void yoneticisiOlanKayit() {
        EmployeeResponse response = mapper.toResponse(
                employeeRepository.findByEmail("ada@example.com").orElseThrow());

        assertThat(response.managerId()).isEqualTo(yoneticiId);
        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.lastName()).isEqualTo("Lovelace");
    }
}
