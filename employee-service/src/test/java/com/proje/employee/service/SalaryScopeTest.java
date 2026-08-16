package com.proje.employee.service;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Ucret ekseni GENEL kapsamdan ayridir.
 *
 * <p>Olculen acik: uc kurali rollerin VEYA'si oldugu icin {@code EMPLOYEE}
 * kapiyi aciyor, servisteki {@code isUnrestricted()} ise (Ik uzmani icin
 * dogru) satir filtresini tamamen kaldiriyordu. Roller BIRLESINCE en dar degil
 * EN GENIS yetki kazaniyordu: tohumlanan yonetici hesabi
 * {@code [EMPLOYEE, HR_SPECIALIST, MANAGER, SYSTEM_ADMIN]} tasiyor ve butun
 * maaslari okuyordu.
 */
@ExtendWith(MockitoExtension.class)
class SalaryScopeTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private OutboxWriter outboxWriter;

    @Mock
    private UserService userService;

    private EmployeeService service() {
        return new EmployeeService(employeeRepository, departmentRepository,
                new EmployeeMapper(), outboxWriter, userService);
    }

    private User userWith(Role first, Role... rest) {
        return new User("someone@example.com", "hash", User.rolesOf(first, rest));
    }

    private void employeeExists(long id) {
        Employee employee = new Employee("Ada", "Lovelace", "ada@example.com",
                new Department("Sales"), "Engineer", LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(employee, "id", id);
        employee.setSalary(new BigDecimal("100000.00"));
        when(employeeRepository.findById(id)).thenReturn(Optional.of(employee));
    }

    @Test
    @DisplayName("An HR specialist who is also an employee cannot read another salary")
    void roleUnionDoesNotWidenSalaryAccess() {
        employeeExists(9L);

        // Kapsam disindaki kayit 403 degil 404 doner: 403 kaydin VAR oldugunu
        // dogrulardi ve id deneyerek kadro sayisi ogrenilebilirdi.
        assertThatThrownBy(() -> service().getSalary(
                9L, AccessScope.forUser(userWith(Role.HR_SPECIALIST, Role.EMPLOYEE))))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("A system administrator cannot read a salary either")
    void systemAdminCannotReadSalary() {
        employeeExists(9L);

        assertThatThrownBy(() -> service().getSalary(
                9L, AccessScope.forUser(userWith(Role.SYSTEM_ADMIN, Role.EMPLOYEE))))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("Only the payroll specialist reads a salary that is not their own")
    void payrollReadsAnySalary() {
        employeeExists(9L);

        assertThat(service().getSalary(9L, AccessScope.forUser(userWith(Role.PAYROLL_SPECIALIST)))
                .salary()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("Everyone else still reads their own salary")
    void ownSalaryStaysReadable() {
        employeeExists(7L);

        assertThat(service().getSalary(7L, new AccessScope(AccessScope.Kind.SELF, 7L, false))
                .salary()).isEqualByComparingTo("100000.00");
    }

    @Test
    @DisplayName("The scope carries the salary axis only for the payroll role")
    void onlyPayrollCarriesTheAxis() {
        assertThat(AccessScope.forUser(userWith(Role.PAYROLL_SPECIALIST))
                .includesAllSalaries()).isTrue();
        assertThat(AccessScope.forUser(userWith(Role.HR_SPECIALIST, Role.SYSTEM_ADMIN))
                .includesAllSalaries()).isFalse();
        assertThat(AccessScope.forUser(userWith(Role.SERVICE))
                .includesAllSalaries()).isFalse();
    }

    @Test
    @DisplayName("An unlinked account reads nobody's salary")
    void unlinkedAccountReadsNothing() {
        employeeExists(9L);

        // employeeId null; ucret ekseni de kapali oldugu icin karsilastirma
        // hicbir kayitla eslesmemeli.
        assertThatThrownBy(() -> service().getSalary(
                9L, new AccessScope(AccessScope.Kind.SELF, null, false)))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("Roles are a set, so the axis does not depend on their order")
    void axisIsOrderIndependent() {
        Set<Role> mixed = User.rolesOf(Role.EMPLOYEE, Role.PAYROLL_SPECIALIST, Role.HR_SPECIALIST);
        User user = new User("someone@example.com", "hash", mixed);

        assertThat(AccessScope.forUser(user).includesAllSalaries()).isTrue();
    }
}
