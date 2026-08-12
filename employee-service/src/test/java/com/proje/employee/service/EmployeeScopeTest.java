package com.proje.employee.service;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.mapper.EmployeeMapper;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kapsamin personel servisinde nasil uygulandigi. */
@ExtendWith(MockitoExtension.class)
class EmployeeScopeTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private UserService userService;

    private final EmployeeMapper mapper = new EmployeeMapper();

    private EmployeeService service() {
        return new EmployeeService(employeeRepository, departmentRepository, mapper, outboxWriter,
                userService);
    }

    private Employee employee(Long id, Employee manager) {
        Employee employee = new Employee("First", "Last", id + "@example.com",
                new Department("Sales"), "Engineer", LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(employee, "id", id);
        employee.setManager(manager);
        return employee;
    }

    @Test
    @DisplayName("Applies no row filter for an unrestricted caller")
    void unrestrictedCallerFiltersNothing() {
        when(employeeRepository.search(any(), any(), isNull(), eq(false), any()))
                .thenReturn(Page.empty());

        service().getAll(null, null, new AccessScope(AccessScope.Kind.ALL, null),
                PageRequest.of(0, 10));

        verify(employeeRepository).search(isNull(), isNull(), isNull(), eq(false), any());
    }

    @Test
    @DisplayName("Restricts the query to the caller's own row")
    void selfScopeFiltersToOwnRow() {
        when(employeeRepository.search(any(), any(), eq(7L), eq(false), any()))
                .thenReturn(Page.empty());

        service().getAll(null, null, new AccessScope(AccessScope.Kind.SELF, 7L),
                PageRequest.of(0, 10));

        // includeReports = false: kendi kaydi disinda hicbir sey.
        verify(employeeRepository).search(isNull(), isNull(), eq(7L), eq(false), any());
    }

    @Test
    @DisplayName("Lets a manager's query include their direct reports")
    void teamScopeIncludesReports() {
        when(employeeRepository.search(any(), any(), eq(7L), eq(true), any()))
                .thenReturn(Page.empty());

        service().getAll(null, null, new AccessScope(AccessScope.Kind.TEAM, 7L),
                PageRequest.of(0, 10));

        verify(employeeRepository).search(isNull(), isNull(), eq(7L), eq(true), any());
    }

    @Test
    @DisplayName("Returns an empty page without querying when the caller has no employee record")
    void unlinkedCallerSeesEmptyPage() {
        Page<?> page = service().getAll(null, null,
                new AccessScope(AccessScope.Kind.SELF, null), PageRequest.of(0, 10));

        assertThat(page).isEmpty();
        // Sorgu HIC atilmaz: kapsam bos oldugu icin sonucun ne olacagi bellidir.
        verify(employeeRepository, never()).search(any(), any(), any(), any(Boolean.class), any());
    }

    @Test
    @DisplayName("Hides a record outside the scope behind a 404, not a 403")
    void hidesOutOfScopeRecordAsMissing() {
        // 403 "bu kayit var ama goremezsin" derdi ve id deneyerek personel
        // sayisi ogrenilebilirdi.
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, null)));

        assertThatThrownBy(() -> service().getById(9L, new AccessScope(AccessScope.Kind.SELF, 7L)))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("Lets an employee read their own record")
    void allowsOwnRecord() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee(7L, null)));

        assertThat(service().getById(7L, new AccessScope(AccessScope.Kind.SELF, 7L)).id())
                .isEqualTo(7L);
    }

    @Test
    @DisplayName("Lets a manager read a direct report but not a stranger")
    void managerReadsOwnReportsOnly() {
        Employee boss = employee(7L, null);
        Employee report = employee(8L, boss);
        Employee stranger = employee(9L, null);

        when(employeeRepository.findById(8L)).thenReturn(Optional.of(report));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(stranger));

        AccessScope team = new AccessScope(AccessScope.Kind.TEAM, 7L);

        assertThat(service().getById(8L, team).id()).isEqualTo(8L);
        assertThatThrownBy(() -> service().getById(9L, team))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("Refuses to list the team of someone the caller cannot see")
    void refusesForeignTeamListing() {
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(employee(9L, null)));

        assertThatThrownBy(() -> service().getDirectReports(9L,
                new AccessScope(AccessScope.Kind.SELF, 7L)))
                .isInstanceOf(EmployeeNotFoundException.class);

        verify(employeeRepository, never()).findByManagerIdOrderByLastNameAsc(any());
    }

    @Test
    @DisplayName("Lets a manager list their own team")
    void allowsOwnTeamListing() {
        Employee boss = employee(7L, null);
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(boss));
        when(employeeRepository.findByManagerIdOrderByLastNameAsc(7L))
                .thenReturn(List.of(employee(8L, boss)));

        assertThat(service().getDirectReports(7L, new AccessScope(AccessScope.Kind.TEAM, 7L)))
                .hasSize(1);
    }
}
