package com.proje.employee.service;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.EmployeeUpdateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.exception.DepartmentNotFoundException;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.exception.ManagerCycleException;
import com.proje.employee.mapper.EmployeeMapper;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Spy
    private EmployeeMapper employeeMapper = new EmployeeMapper();

    @InjectMocks
    private EmployeeService employeeService;

    private EmployeeCreateRequest istek(Long departmentId, Long managerId) {
        return new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", "+90 555 123 45 67",
                departmentId, managerId, "Software Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("85000.00"));
    }

    @Test
    @DisplayName("Kayitli email ile olusturma reddedilir ve kayit denenmez")
    void kayitliEmailReddedilir() {
        when(employeeRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(istek(1L, null)))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ada@example.com");

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Olmayan departman ile olusturma reddedilir")
    void olmayanDepartmanReddedilir() {
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(istek(99L, null)))
                .isInstanceOf(DepartmentNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Olmayan yonetici ile olusturma reddedilir")
    void olmayanYoneticiReddedilir() {
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(new Department("Sales")));
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(istek(1L, 99L)))
                .isInstanceOf(EmployeeNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gecerli istek kaydedilir ve alanlar entity'ye tasinir")
    void gecerliIstekKaydedilir() {
        Department department = new Department("Sales");
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        EmployeeResponse response = employeeService.create(istek(1L, null));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee kaydedilen = captor.getValue();

        assertThat(kaydedilen.getFirstName()).isEqualTo("Ada");
        assertThat(kaydedilen.getEmail()).isEqualTo("ada@example.com");
        assertThat(kaydedilen.getPhone()).isEqualTo("+90 555 123 45 67");
        assertThat(kaydedilen.getSalary()).isEqualByComparingTo("85000.00");
        assertThat(kaydedilen.getDepartment()).isSameAs(department);
        assertThat(kaydedilen.getManager()).isNull();

        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.departmentName()).isEqualTo("Sales");
    }

    @Test
    @DisplayName("Olmayan id ile sorgulama EmployeeNotFoundException firlatir")
    void olmayanIdSorgulama() {
        when(employeeRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(42L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("42");
    }

    private Employee calisan(Long id, String email, Department department) {
        Employee e = new Employee("Ad", "Soyad", email, department,
                "Engineer", LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private EmployeeUpdateRequest guncelleme(String email, Long departmentId, Long managerId) {
        return new EmployeeUpdateRequest("Ad", "Soyad", email, null,
                departmentId, managerId, "Engineer",
                LocalDate.of(2024, 1, 1), null);
    }

    @Test
    @DisplayName("Kisi kendi yoneticisi yapilamaz")
    void kendiYoneticisiOlamaz() {
        Department department = new Department("Sales");
        Employee ada = calisan(1L, "ada@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        assertThatThrownBy(() ->
                employeeService.update(1L, guncelleme("ada@example.com", 1L, 1L)))
                .isInstanceOf(ManagerCycleException.class);
    }

    @Test
    @DisplayName("A -> B -> A dongusu reddedilir (veritabani kisitinin goremedigi durum)")
    void dolayliDonguReddedilir() {
        Department department = new Department("Sales");
        Employee ada = calisan(1L, "ada@example.com", department);
        Employee grace = calisan(2L, "grace@example.com", department);

        // Grace'in yoneticisi zaten Ada. Simdi Ada'nin yoneticisini Grace yapmaya
        // calisiyoruz -> Ada -> Grace -> Ada dongusu olusur.
        grace.setManager(ada);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(grace));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        assertThatThrownBy(() ->
                employeeService.update(1L, guncelleme("ada@example.com", 1L, 2L)))
                .isInstanceOf(ManagerCycleException.class);
    }

    @Test
    @DisplayName("Dongu olusturmayan yonetici atamasi kabul edilir")
    void gecerliYoneticiAtamasiKabulEdilir() {
        Department department = new Department("Sales");
        Employee ada = calisan(1L, "ada@example.com", department);
        Employee grace = calisan(2L, "grace@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(grace));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        employeeService.update(1L, guncelleme("ada@example.com", 1L, 2L));

        assertThat(ada.getManager()).isSameAs(grace);
    }

    @Test
    @DisplayName("Email degismediyse tekillik kontrolu yapilmaz")
    void ayniEmailIleGuncellemeGecer() {
        Department department = new Department("Sales");
        Employee ada = calisan(1L, "ada@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        employeeService.update(1L, guncelleme("ada@example.com", 1L, null));

        verify(employeeRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("Baskasina ait email ile guncelleme reddedilir")
    void baskasininEmailiIleGuncellemeReddedilir() {
        Department department = new Department("Sales");
        Employee ada = calisan(1L, "ada@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(employeeRepository.existsByEmail("grace@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                employeeService.update(1L, guncelleme("grace@example.com", 1L, null)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Pasiflestirme kaydi silmez, active alanini false yapar")
    void pasiflestirmeKaydiSilmez() {
        Employee ada = calisan(1L, "ada@example.com", new Department("Sales"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.deactivate(1L);

        assertThat(ada.isActive()).isFalse();
        verify(employeeRepository, never()).delete(any());
    }
}
