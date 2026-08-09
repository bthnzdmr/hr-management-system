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
        List<Department> departments = departmentRepository.findAll();

        for (int i = 0; i < 5; i++) {
            Employee e = new Employee(
                    "Ad" + i,
                    "Soyad" + i,
                    "test" + i + "@example.com",
                    departments.get(i),
                    "Engineer",
                    LocalDate.of(2024, 1, 1));
            employeeRepository.save(e);
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
    @DisplayName("JOIN FETCH olmadan her departman icin ayri sorgu atilir (N+1)")
    void joinFetchOlmadanNArtiBirSorguAtilir() {
        List<Employee> employees = employeeRepository.findAll();

        // Vekil nesneye dokunmak veritabanina gidilmesine sebep olur.
        employees.forEach(e -> e.getDepartment().getName());

        long sorguSayisi = statistics.getPrepareStatementCount();

        assertThat(employees).hasSize(5);
        assertThat(sorguSayisi).isEqualTo(6);   // 1 liste + 5 departman
    }

    @Test
    @DisplayName("JOIN FETCH ile departmanlar ayni sorguda gelir")
    void joinFetchIleTekSorguAtilir() {
        Page<Employee> page = employeeRepository.findAllWithDepartment(
                PageRequest.of(0, 10, Sort.by("lastName")));

        page.getContent().forEach(e -> e.getDepartment().getName());

        long sorguSayisi = statistics.getPrepareStatementCount();

        assertThat(page.getContent()).hasSize(5);

        // Tek sorgu: departmanlar JOIN ile geldigi icin dongude veritabanina gidilmiyor.
        // Sayim sorgusu da atilmiyor -- ilk sayfada ve sonuc sayfa boyutundan az oldugu
        // icin Spring Data toplami zaten biliyor.
        assertThat(sorguSayisi).isEqualTo(1);
    }

    @Test
    @DisplayName("Turetilmis sorgu email ile kaydi bulur")
    void findByEmailKaydiBulur() {
        assertThat(employeeRepository.findByEmail("test0@example.com")).isPresent();
        assertThat(employeeRepository.findByEmail("yok@example.com")).isEmpty();
        assertThat(employeeRepository.existsByEmail("test1@example.com")).isTrue();
    }
}
