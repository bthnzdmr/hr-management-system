package com.proje.employee.service;

import com.proje.employee.dto.EmployeeCreateRequest;
import com.proje.employee.dto.EmployeeResponse;
import com.proje.employee.dto.EmployeeUpdateRequest;
import com.proje.employee.dto.SalaryUpdateRequest;
import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.TerminationReason;
import com.proje.employee.event.EmployeeEvent;
import com.proje.employee.event.EmployeeEventType;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.exception.DepartmentNotFoundException;
import com.proje.employee.exception.EmailAlreadyExistsException;
import com.proje.employee.exception.EmployeeNotFoundException;
import com.proje.employee.exception.InactiveManagerException;
import com.proje.employee.exception.ManagerCycleException;
import com.proje.employee.exception.MissingTerminationReasonException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    /** Kapsam testleri ayri dosyada; burada kisitsiz gorunum varsayilir. */
    private static final AccessScope UNRESTRICTED =
            new AccessScope(AccessScope.Kind.ALL, null);

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private UserService userService;

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
                LocalDate.of(2024, 1, 1));
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
    @DisplayName("Wraps the search text in wildcards and lowercases it")
    void wrapsSearchTextInWildcards() {
        when(employeeRepository.search(any(), any(), any(), anyBoolean(), any())).thenReturn(Page.empty());

        employeeService.getAll("  LoVe  ", true, UNRESTRICTED, PageRequest.of(0, 10));

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(employeeRepository).search(captor.capture(), eq(true), isNull(), anyBoolean(), any());
        assertThat(captor.getValue()).isEqualTo("%love%");
    }

    @Test
    @DisplayName("Passes no search pattern when the text is blank")
    void passesNoPatternForBlankSearch() {
        // null gecmek sorgudaki kosulu tamamen devre disi birakir; bos dizge
        // gecseydi "%%" deseni her satirla eslesir ama gereksiz is yaratirdi.
        when(employeeRepository.search(any(), any(), any(), anyBoolean(), any())).thenReturn(Page.empty());

        employeeService.getAll("   ", null, UNRESTRICTED, PageRequest.of(0, 10));

        verify(employeeRepository).search(isNull(), isNull(), isNull(), anyBoolean(), any());
    }

    @Test
    @DisplayName("Throws EmployeeNotFoundException for a missing id")
    void throwsForMissingId() {
        when(employeeRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.getById(42L, UNRESTRICTED))
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
        // Grace'ten yukari zincir: Grace -> Ada. Ada zincirde oldugu icin
        // Ada'yi Grace'e baglamak dongu uretir.
        when(employeeRepository.findAncestorIds(eq(2L), anyInt()))
                .thenReturn(List.of(2L, 1L));

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
        // Grace'in ustunde kimse yok: zincir yalnizca Grace.
        when(employeeRepository.findAncestorIds(eq(2L), anyInt())).thenReturn(List.of(2L));

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
    @DisplayName("A general update cannot touch the salary")
    void generalUpdateLeavesSalaryUntouched() {
        // Bu testin varlik sebebi gercek bir veri kaybi hatasidir: maas genel
        // istegin parcasiyken, onu okuyamayan istemci her guncellemede siliyordu.
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);
        ada.setSalary(new BigDecimal("95000.00"));

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));

        employeeService.update(1L, updateRequest("ada@example.com", 1L, null));

        assertThat(ada.getSalary()).isEqualByComparingTo("95000.00");
    }

    @Test
    @DisplayName("Reads the salary through its own endpoint")
    void readsSalaryThroughItsOwnEndpoint() {
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        ada.setSalary(new BigDecimal("95000.00"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        assertThat(employeeService.getSalary(1L).salary()).isEqualByComparingTo("95000.00");
    }

    @Test
    @DisplayName("Updating the salary publishes an event that does not carry it")
    void salaryUpdatePublishesEventWithoutSalary() {
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        ada.setSalary(new BigDecimal("95000.00"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.updateSalary(1L, new SalaryUpdateRequest(new BigDecimal("110000.00")));

        assertThat(ada.getSalary()).isEqualByComparingTo("110000.00");

        EmployeeEvent event = capturePublishedEvent();
        assertThat(event.eventType()).isEqualTo(EmployeeEventType.UPDATED);
        // Olay maasi tasimaz: bildirim maili sifresiz SMTP uzerinden gidiyor.
        assertThat(event.toString()).doesNotContain("110000");
    }

    @Test
    @DisplayName("Rejects a salary update for a missing employee")
    void rejectsSalaryUpdateForMissingEmployee() {
        when(employeeRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                employeeService.updateSalary(42L, new SalaryUpdateRequest(new BigDecimal("1.00"))))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("Deactivation flags the record instead of deleting it")
    void deactivationDoesNotDelete() {
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.changeStatus(1L, false, TerminationReason.RESIGNED);

        assertThat(ada.isActive()).isFalse();
        verify(employeeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Brings a deactivated employee back and announces it as REACTIVATED")
    void reactivationRestoresTheEmployee() {
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        ada.terminate(LocalDate.of(2026, 1, 1), TerminationReason.RESIGNED);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.changeStatus(1L, true, null);

        assertThat(ada.isActive()).isTrue();
        assertThat(capturePublishedEvent().eventType()).isEqualTo(EmployeeEventType.REACTIVATED);
    }

    @Test
    @DisplayName("Refuses to deactivate without a termination reason and changes nothing")
    void refusesTerminationWithoutReason() {
        // Varsayilan bir sebep atamak daha kolay olurdu ama devir oraninin en
        // anlamli kirilimini -- istege bagli ayrilma / isten cikarma -- sessizce
        // bozardi. Eksik veri, yanlis veriden iyidir.
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        assertThatThrownBy(() -> employeeService.changeStatus(1L, false, null))
                .isInstanceOf(MissingTerminationReasonException.class);

        assertThat(ada.isActive()).isTrue();
        assertThat(ada.getTerminatedAt()).isNull();
        verify(outboxWriter, never()).write(any());
    }

    @Test
    @DisplayName("Records when and why the employee left")
    void recordsTerminationDetails() {
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.changeStatus(1L, false, TerminationReason.DISMISSED);

        assertThat(ada.getTerminationReason()).isEqualTo(TerminationReason.DISMISSED);
        // Tarihi SUNUCU koyar; istemciye birakilsaydi gecmise donuk kayit
        // girilip devir orani sekillendirilebilirdi.
        assertThat(ada.getTerminatedAt()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Closes the account of an employee who left")
    void closesAccountOnTermination() {
        // JML'in "leaver" adimi. Olculdu: bu baglanti olmadan ayrilan
        // personelin hesabiyla giris yapilabiliyordu.
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.changeStatus(1L, false, TerminationReason.RESIGNED);

        verify(userService).disableAccountOf(1L);
    }

    @Test
    @DisplayName("Does not reopen the account when the employee is hired back")
    void doesNotReopenAccountOnReactivation() {
        // Erisimi geri vermek BILINCLI bir karar olmali; kisi ayni role
        // donmeyebilir ve sessizce eski yetkileriyle girmemeli.
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        ada.terminate(LocalDate.of(2026, 1, 1), TerminationReason.RESIGNED);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.changeStatus(1L, true, null);

        verify(userService, never()).disableAccountOf(any());
    }

    @Test
    @DisplayName("Clears the termination details when the employee comes back")
    void reactivationClearsTerminationDetails() {
        // "Aktif ama ayrilmis" diye bir durum yoktur; veritabanindaki CHECK
        // kisiti da bunu reddeder.
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        ada.terminate(LocalDate.of(2026, 1, 1), TerminationReason.RESIGNED);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.changeStatus(1L, true, null);

        assertThat(ada.isActive()).isTrue();
        assertThat(ada.getTerminatedAt()).isNull();
        assertThat(ada.getTerminationReason()).isNull();
    }

    @Test
    @DisplayName("Refuses to assign an inactive employee as a manager")
    void refusesInactiveManager() {
        // Mevcut atamalar korunur ama YENI kimse pasif birine baglanamaz.
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);
        Employee retired = employeeWithId(9L, "retired@example.com", department);
        retired.terminate(LocalDate.of(2026, 1, 1), TerminationReason.RESIGNED);

        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() ->
                employeeService.update(1L, updateRequest("ada@example.com", 1L, 9L)))
                .isInstanceOf(InactiveManagerException.class);
    }

    @Test
    @DisplayName("Refuses to create an employee under an inactive manager")
    void refusesInactiveManagerOnCreate() {
        // Uctan uca kosuda yakalandi: kural yalnizca guncellemede geceriydi,
        // olusturma kendi arama kodunu yazdigi icin ayni kontrolu atliyordu.
        Department department = new Department("Sales");
        Employee retired = employeeWithId(9L, "retired@example.com", department);
        retired.terminate(LocalDate.of(2026, 1, 1), TerminationReason.RESIGNED);

        when(employeeRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.findById(9L)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> employeeService.create(createRequest(1L, 9L)))
                .isInstanceOf(InactiveManagerException.class);

        // Hicbir sey yazilmamis olmali: reddedilen bir istek yarim kayit birakmaz.
        verify(employeeRepository, never()).save(any());
        verify(outboxWriter, never()).write(any());
    }

    @Test
    @DisplayName("Lists the people who report directly to an employee")
    void listsDirectReports() {
        Department department = new Department("Sales");
        when(employeeRepository.findById(1L))
                .thenReturn(Optional.of(employeeWithId(1L, "boss@example.com", department)));
        when(employeeRepository.findByManagerIdOrderByLastNameAsc(1L))
                .thenReturn(List.of(employeeWithId(2L, "a@example.com", department)));

        assertThat(employeeService.getDirectReports(1L, UNRESTRICTED)).hasSize(1);
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

        employeeService.changeStatus(1L, false, TerminationReason.RESIGNED);

        EmployeeEvent event = capturePublishedEvent();
        assertThat(event.eventType()).isEqualTo(EmployeeEventType.DEACTIVATED);
        assertThat(event.employeeId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Deactivating an already inactive employee changes nothing and publishes nothing")
    void deactivationIsIdempotent() {
        // Aksi halde her tekrar YENI bir eventId uretir; tuketicinin eventId'ye
        // dayanan idempotency'si bunu ayiklayamaz ve personel mail yagmuruna tutulur.
        Employee ada = employeeWithId(1L, "ada@example.com", new Department("Sales"));
        ada.terminate(LocalDate.of(2026, 1, 1), TerminationReason.RESIGNED);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));

        employeeService.changeStatus(1L, false, TerminationReason.RESIGNED);

        verify(outboxWriter, never()).write(any());
    }

    @Test
    @DisplayName("Fails loudly instead of accepting the assignment when the chain is too deep")
    void failsLoudlyOnUnreasonablyDeepChain() {
        // Bozuk veride dongu ust sinira kadar yurur. Onceden dongu burada
        // sessizce bitiyor ve atama KABUL EDILIYORDU.
        Department department = new Department("Sales");
        Employee ada = employeeWithId(1L, "ada@example.com", department);

        Employee head = employeeWithId(1000L, "m1000@example.com", department);

        // Sorgu derinlik sigortasinda durur ve TAM ust sinir kadar satir doner:
        // bu, zincirin daha da uzadigi (veya dongu icerdigi) anlamina gelir.
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(ada));
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(department));
        when(employeeRepository.findById(1000L)).thenReturn(Optional.of(head));
        when(employeeRepository.findAncestorIds(eq(1000L), anyInt()))
                .thenReturn(java.util.stream.LongStream.rangeClosed(1000L, 1099L)
                        .boxed().toList());

        assertThatThrownBy(() ->
                employeeService.update(1L, updateRequest("ada@example.com", 1L, 1000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Manager chain exceeded");
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
