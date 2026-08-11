package com.proje.employee.config;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Demo verisinin kendi ic tutarliligi.
 *
 * Listede bir yazim hatasi olsaydi uygulama ACILISTA patlardi; bu testler onu
 * saniyeler icinde yakalar. Veritabani gerekmez.
 */
@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    private static final List<String> SEEDED_DEPARTMENTS = List.of(
            "Software Development", "Human Resources", "Accounting", "Sales", "Marketing");

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private final DemoDataSeeder seeder = new DemoDataSeeder();

    /** Kaydedilen her personeli e-postasiyla geri verebilen sahte depo. */
    private Map<String, Employee> wireRepositories() {
        Map<String, Employee> stored = new HashMap<>();

        when(departmentRepository.findByName(anyString()))
                .thenAnswer(call -> Optional.of(new Department(call.getArgument(0))));

        when(employeeRepository.save(any(Employee.class))).thenAnswer(call -> {
            Employee employee = call.getArgument(0);
            stored.put(employee.getEmail(), employee);
            return employee;
        });

        when(employeeRepository.findByEmail(anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.getArgument(0))));

        when(departmentRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        return stored;
    }

    @Test
    @DisplayName("Every manager reference points at somebody in the same data set")
    void managerReferencesResolve() {
        // Bir yazim hatasi burada patlar, uygulama acilisinda degil.
        wireRepositories();

        seeder.seed(employeeRepository, departmentRepository);
    }

    @Test
    @DisplayName("Uses only departments that the schema actually seeds")
    void usesOnlySeededDepartments() {
        List<String> requested = new ArrayList<>();
        when(departmentRepository.findByName(anyString())).thenAnswer(call -> {
            requested.add(call.getArgument(0));
            return Optional.of(new Department(call.getArgument(0)));
        });
        when(employeeRepository.save(any(Employee.class))).thenAnswer(call -> call.getArgument(0));
        when(employeeRepository.findByEmail(anyString())).thenAnswer(call ->
                Optional.of(new Employee("A", "B", call.getArgument(0),
                        new Department("Software Development"), "Engineer",
                        java.time.LocalDate.of(2024, 1, 1))));
        when(departmentRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        seeder.seed(employeeRepository, departmentRepository);

        assertThat(requested).isSubsetOf(SEEDED_DEPARTMENTS);
    }

    @Test
    @DisplayName("Spreads people across every department and several hire years")
    void producesVariedData() {
        // Panelin tek cubuk gostermemesi icin cesitlilik SART; demo verinin
        // varlik sebebi budur.
        Map<String, Employee> stored = wireRepositories();

        seeder.seed(employeeRepository, departmentRepository);

        assertThat(stored.values().stream()
                .map(employee -> employee.getDepartment().getName())
                .distinct())
                .hasSize(SEEDED_DEPARTMENTS.size());

        assertThat(stored.values().stream()
                .map(employee -> employee.getHireDate().getYear())
                .distinct())
                .hasSizeGreaterThanOrEqualTo(5);

        assertThat(stored.values().stream().anyMatch(employee -> !employee.isActive())).isTrue();
        assertThat(stored.values().stream().anyMatch(employee -> employee.getManager() != null))
                .isTrue();
    }

    @Test
    @DisplayName("Persists the manager links instead of relying on dirty checking")
    void persistsManagerLinks() {
        // Gercekten yasandi: ikinci gecis yalnizca setManager cagiriyordu ve
        // 36 kaydin HICBIRINDE yonetici yazilmadi. Kirli kontrol yalnizca
        // aktif bir transaction icindeki YONETILEN entity icin calisir;
        // burada cagri kendi sinifindan geldigi icin proxy atlaniyor.
        wireRepositories();

        seeder.seed(employeeRepository, departmentRepository);

        ArgumentCaptor<Employee> savedArgs = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository, atLeastOnce()).save(savedArgs.capture());

        // Yoneticisi atanan kayitlar IKI kez kaydedilmis olmali: once kendisi,
        // sonra yonetici baglandiktan sonra. Sayiya degil, ayni kaydin tekrar
        // yazilmasina bakilir -- olcut davranisin kendisidir.
        Map<String, Long> savesPerEmail = savedArgs.getAllValues().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Employee::getEmail, java.util.stream.Collectors.counting()));

        assertThat(savesPerEmail.values().stream().filter(count -> count >= 2).count())
                .as("employees whose manager link was written back")
                .isGreaterThanOrEqualTo(25);
    }

    @Test
    @DisplayName("Writes nothing when the data is already there")
    void isIdempotent() {
        // Her yeniden baslatmada tekrar yazsaydi kayitlar katlanirdi.
        when(employeeRepository.existsByEmail(anyString())).thenReturn(true);

        seeder.seed(employeeRepository, departmentRepository);

        verify(employeeRepository, never()).save(any());
    }
}
