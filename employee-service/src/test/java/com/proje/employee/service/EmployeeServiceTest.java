package com.proje.employee.service;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.EmployeeUpdateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.event.EmployeeEvent;
import com.proje.employee.event.EmployeeEventType;
import com.proje.employee.event.OutboxWriter;
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

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private EmployeeMapper employeeMapper = new EmployeeMapper();

    @InjectMocks
    private EmployeeService employeeService;

    private EmployeeCreateRequest createRequest(Long departmentId, Long managerId) {
        return new EmployeeCreateRequest(
                "Ada", "Lovelace", "ada@example.com", "+90 555 123 45 67",
                departmentId, managerId, "Software Engineer",
                LocalDate.of(2024, 1, 15), new BigDecimal("85000.00"));
    }

    private EmployeeUpdateRequest updateRequest(String email, Long departmentId, Long managerId) {
        return new EmployeeUpdateRequest("Ada", "Lovelace", email, null,
                departmentId, managerId, "Engineer",
                LocalDate.of(2024, 1, 1), null);
    }

    private Employee employeeWithId(Long id, String email, Department department) {
        Employee employee = new Employee("First", "Last", email, department,
                "Engineer", LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    @Test
    @DisplayName("Rejects creation with an already registered email and saves nothing")
    void rejectsDuplicateEmail() {
        when(employeeRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(createRequest(1L, null)))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("ada@example.com");

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects creation with a non-existent department")
    void rejectsMissingDepartment() {
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(departmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(createRequest(99L, null)))
                .isInstanceOf(DepartmentNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects creation with a non-existent manager")
    void rejectsMissingManager() {
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(new Department("Sales")));
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.create(createRequest(1L, 99L)))
                .isInstanceOf(EmployeeNotFoundException.class);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Saves a valid request and copies every field onto the entity")
    void savesValidRequest() {
        Department department = new Department("Sales");
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        EmployeeResponse response = employeeService.create(createRequest(1L, null));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();

        assertThat(saved.getFirstName()).isEqualTo("Ada");
        assertThat(saved.getEmail()).isEqualTo("ada@example.com");
        assertThat(saved.getPhone()).isEqualTo("+90 555 123 45 67");
        assertThat(saved.getSalary()).isEqualByComparingTo("85000.00");
        assertThat(saved.getDepartment()).isSameAs(department);
        assertThat(saved.getManager()).isNull();

        assertThat(response.firstName()).isEqualTo("Ada");
        assertThat(response.departmentName()).isEqualTo("Sales");
    }

    @Test
    @DisplayName("Throws EmployeeNotFoundException for a missing id")
    void throwsForMissingId() {
        when(employeeRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(42L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    @DisplayName("An employee cannot be their own manager")
    void rejectsSelfAsManager() {
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        assertThatThrownBy(() ->
                employeeService.update(1L, updateRequest("ada@example.com", 1L, 1L)))
                .isInstanceOf(ManagerCycleException.class);
    }

    @Test
    @DisplayName("Rejects an A -> B -> A cycle that a CHECK constraint cannot catch")
    void rejectsIndirectCycle() {
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);
        Employee grace = employeeWithId(2L, "grace@example.com", department);

        // Grace'in yoneticisi zaten Ada. Simdi Ada'nin yoneticisini Grace yapmaya
        // calisiyoruz -> Ada -> Grace -> Ada dongusu olusur.
        grace.setManager(ada);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(grace));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        assertThatThrownBy(() ->
                employeeService.update(1L, updateRequest("ada@example.com", 1L, 2L)))
                .isInstanceOf(ManagerCycleException.class);
    }

    @Test
    @DisplayName("Accepts a manager assignment that creates no cycle")
    void acceptsValidManagerAssignment() {
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);
        Employee grace = employeeWithId(2L, "grace@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(grace));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        employeeService.update(1L, updateRequest("ada@example.com", 1L, 2L));

        assertThat(ada.getManager()).isSameAs(grace);
    }

    @Test
    @DisplayName("Skips the uniqueness check when the email did not change")
    void skipsUniquenessCheckWhenEmailUnchanged() {
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        employeeService.update(1L, updateRequest("ada@example.com", 1L, null));

        verify(employeeRepository, never()).existsByEmail(any());
    }

    @Test
    @DisplayName("Rejects an update using an email that belongs to someone else")
    void rejectsUpdateWithTakenEmail() {
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(employeeRepository.existsByEmail("grace@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
                employeeService.update(1L, updateRequest("grace@example.com", 1L, null)))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }

    @Test
    @DisplayName("Deactivation flags the record instead of deleting it")
    void deactivationDoesNotDelete() {
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.deactivate(1L);

        assertThat(ada.isActive()).isFalse();
        verify(employeeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Publishes a CREATED event carrying a snapshot of the saved employee")
    void publishesCreatedEvent() {
        Department department = new Department("Sales");
        when(employeeRepository.existsByEmail(any())).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));

        employeeService.create(createRequest(1L, null));

        EmployeeEvent event = capturePublishedEvent();
        assertThat(event.eventType()).isEqualTo(EmployeeEventType.CREATED);
        assertThat(event.email()).isEqualTo("ada@example.com");
        assertThat(event.departmentName()).isEqualTo("Sales");
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Publishes an UPDATED event after a successful update")
    void publishesUpdatedEvent() {
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        employeeService.update(1L, updateRequest("ada@example.com", 1L, null));

        assertThat(capturePublishedEvent().eventType()).isEqualTo(EmployeeEventType.UPDATED);
    }

    @Test
    @DisplayName("Publishes a DEACTIVATED event when an employee is deactivated")
    void publishesDeactivatedEvent() {
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.deactivate(1L);

        EmployeeEvent event = capturePublishedEvent();
        assertThat(event.eventType()).isEqualTo(EmployeeEventType.DEACTIVATED);
        assertThat(event.employeeId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Publishes nothing when creation fails validation")
    void publishesNothingWhenCreationFails() {
        when(employeeRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(createRequest(1L, null)))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(outboxWriter, never()).write(any());
    }

    private EmployeeEvent capturePublishedEvent() {
        ArgumentCaptor<EmployeeEvent> captor = ArgumentCaptor.forClass(EmployeeEvent.class);
        verify(outboxWriter).write(captor.capture());
        return captor.getValue();
    }
}
