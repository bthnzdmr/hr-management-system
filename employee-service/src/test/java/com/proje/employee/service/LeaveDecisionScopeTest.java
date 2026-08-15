package com.proje.employee.service;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.exception.LeaveRequestNotFoundException;
import com.proje.employee.exception.LeaveRuleViolationException;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Izin kararini kim verebilir. */
@ExtendWith(MockitoExtension.class)
class LeaveDecisionScopeTest {

    @Mock
    private LeaveRequestRepository leaveRequests;

    @Mock
    private EmployeeRepository employees;

    private LeaveRequestService service;
    private Employee grace;
    private Employee ada;
    private User decider;

    @BeforeEach
    void setUp() {
        service = new LeaveRequestService(leaveRequests, employees);

        Department department = new Department("Software Development");
        grace = employee(212L, "Grace", "Hopper", department, null);
        ada = employee(218L, "Ada", "Lovelace", department, grace);
        decider = new User("grace@example.com", "hash", Set.of(Role.MANAGER));
    }

    private Employee employee(long id, String first, String last, Department dept, Employee manager) {
        Employee e = new Employee(first, last, first + "@example.com", dept, "Engineer", LocalDate.now());
        ReflectionTestUtils.setField(e, "id", id);
        e.setManager(manager);
        return e;
    }

    private LeaveRequest leaveOf(Employee owner) {
        LeaveRequest leave = new LeaveRequest(owner, decider, LeaveType.ANNUAL,
                LocalDate.of(2032, 3, 10), LocalDate.of(2032, 3, 15), null);
        ReflectionTestUtils.setField(leave, "id", 7L);
        when(leaveRequests.findByIdWithEmployee(7L)).thenReturn(Optional.of(leave));
        return leave;
    }

    private AccessScope managerScope() {
        return new AccessScope(AccessScope.Kind.TEAM, grace.getId());
    }

    @Test
    @DisplayName("a manager may approve leave for a direct report")
    void managerApprovesDirectReport() {
        leaveOf(ada);

        assertThat(service.approve(7L, decider, managerScope()).status())
                .isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    @DisplayName("a manager may not approve their own leave")
    void managerCannotApproveOwnLeave() {
        // Kendi iznini onaylamak gorevler ayriligina aykiri; kimse kendi
        // rolune dokunamaz kuralinin ayni ailesinden.
        leaveOf(grace);

        assertThatThrownBy(() -> service.approve(7L, decider, managerScope()))
                .isInstanceOf(LeaveRuleViolationException.class)
                .hasMessageContaining("your own leave");
    }

    @Test
    @DisplayName("a manager may not decide leave for someone outside their team")
    void managerCannotDecideForStranger() {
        Employee stranger = employee(300L, "Alan", "Kay", new Department("Sales"), null);
        leaveOf(stranger);

        assertThatThrownBy(() -> service.approve(7L, decider, managerScope()))
                .isInstanceOf(LeaveRequestNotFoundException.class);
    }

    @Test
    @DisplayName("HR may decide leave for someone with no manager at all")
    void hrDecidesForPeopleWithoutAManager() {
        // Bes departman baskaninin yoneticisi yok; Ik yedegi olmasaydi
        // izinleri askida kalirdi.
        leaveOf(grace);

        assertThat(service.approve(7L, decider, new AccessScope(AccessScope.Kind.ALL, null))
                .status()).isEqualTo(LeaveStatus.APPROVED);
    }

    @Test
    @DisplayName("an employee may not decide anyone's leave, not even their own")
    void employeeCannotDecide() {
        leaveOf(ada);

        assertThatThrownBy(() -> service.approve(7L, decider,
                new AccessScope(AccessScope.Kind.SELF, ada.getId())))
                .isInstanceOf(LeaveRuleViolationException.class);
    }
}
