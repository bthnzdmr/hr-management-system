package com.proje.employee.service;

import com.proje.employee.entity.Department;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveType;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.repository.DepartmentRepository;
import com.proje.employee.repository.EmployeeRepository;
import com.proje.employee.repository.LeaveRequestRepository;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Cakisma kisitinin GERCEK veritabaninda ne firlattigi. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LeaveOverlapConstraintTest {

    @Autowired
    private LeaveRequestRepository leaveRequests;

    @Autowired
    private EmployeeRepository employees;

    @Autowired
    private UserRepository users;

    @Autowired
    private DepartmentRepository departments;

    private Employee employee;
    private User author;

    @BeforeEach
    void setUp() {
        leaveRequests.deleteAllInBatch();

        Department department = departments.findAllByOrderByNameAsc().stream().findFirst()
                .orElseGet(() -> departments.saveAndFlush(new Department("Leave test dept")));

        employee = employees.saveAndFlush(new Employee("Leave", "Tester",
                "leave.tester@example.com", department, "Engineer", LocalDate.now()));

        author = users.saveAndFlush(new User("leave.author@example.com", "hash",
                Set.of(Role.HR_SPECIALIST)));
    }

    @Test
    @DisplayName("rejects a second leave whose dates overlap an existing one")
    void rejectsOverlappingLeave() {
        leaveRequests.saveAndFlush(leave(LocalDate.of(2030, 3, 10), LocalDate.of(2030, 3, 15)));

        assertThatThrownBy(() ->
                leaveRequests.saveAndFlush(leave(LocalDate.of(2030, 3, 12), LocalDate.of(2030, 3, 18))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("reports SQLState 23P01 so the violation can be told apart")
    void exposesTheExclusionSqlState() {
        // OLCULDU VE CURUTULDU: once Hibernate'in getConstraintName() degeri
        // okunuyordu, dislama kisiti icin NULL donuyor. Tanima ona dayansaydi
        leaveRequests.saveAndFlush(leave(LocalDate.of(2030, 5, 1), LocalDate.of(2030, 5, 5)));

        Throwable thrown = catchThrowable(() ->
                leaveRequests.saveAndFlush(leave(LocalDate.of(2030, 5, 3), LocalDate.of(2030, 5, 9))));

        SQLException sql = null;
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException found) {
                sql = found;
                break;
            }
        }

        assertThat((Throwable) sql).isNotNull();
        assertThat(sql.getSQLState()).isEqualTo("23P01");
        // Ad mesajin icinde geciyor; ikinci bir dislama kisiti eklenirse
        // SQLState tek basina ayirt etmezdi.
        assertThat(sql.getMessage()).contains("ex_leave_no_overlap");
    }

    @Test
    @DisplayName("allows a leave that starts the day the previous one ends")
    void allowsAdjacentLeave() {
        // [) semantigi: 15'te biten izinden sonra 16'da baslayan izin cakisma
        // DEGILDIR. Yanlis kurulsaydi son gun sessizce yutulurdu.
        leaveRequests.saveAndFlush(leave(LocalDate.of(2030, 7, 10), LocalDate.of(2030, 7, 15)));

        assertThat(leaveRequests.saveAndFlush(
                leave(LocalDate.of(2030, 7, 16), LocalDate.of(2030, 7, 20))).getId()).isNotNull();
    }

    private LeaveRequest leave(LocalDate start, LocalDate end) {
        return new LeaveRequest(employee, author, LeaveType.ANNUAL, start, end, null);
    }
}
