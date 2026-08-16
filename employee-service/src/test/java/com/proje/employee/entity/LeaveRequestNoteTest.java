package com.proje.employee.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Talebin gerekcesi ile kararin gerekcesi iki ayri olgudur.
 *
 * <p>Olculen veri kaybi: tek kolon vardi ve reddetme onu uzerine yaziyordu.
 * Karar notu verilmediginde alan {@code null} oluyor ve "hastane randevusu"
 * diye girilmis talep gerekcesi GERI DONUSSUZ kayboluyordu -- cevapta tek alan
 * oldugu icin kimse fark etmiyordu.
 */
class LeaveRequestNoteTest {

    private LeaveRequest pendingLeave() {
        Employee ada = new Employee("Ada", "Lovelace", "ada@example.com",
                new Department("Sales"), "Engineer", LocalDate.of(2024, 1, 1));
        User author = new User("hr@example.com", "hash", User.rolesOf(Role.HR_SPECIALIST));

        return new LeaveRequest(ada, author, LeaveType.ANNUAL,
                LocalDate.of(2031, 3, 10), LocalDate.of(2031, 3, 15),
                "ORIGINAL REASON: hospital appointment");
    }

    private User decider() {
        return new User("manager@example.com", "hash", User.rolesOf(Role.MANAGER));
    }

    @Test
    @DisplayName("Rejecting keeps the reason the requester gave")
    void rejectingKeepsTheRequestersNote() {
        LeaveRequest leave = pendingLeave();

        leave.reject(decider(), "Team is short-staffed that week");

        assertThat(leave.getNote()).isEqualTo("ORIGINAL REASON: hospital appointment");
        assertThat(leave.getDecisionNote()).isEqualTo("Team is short-staffed that week");
    }

    @Test
    @DisplayName("Rejecting without a reason does not erase the request either")
    void rejectingWithoutANoteKeepsTheOriginal() {
        // En kotu hal buydu: karar notu verilmeyince talep gerekcesi null olurdu.
        LeaveRequest leave = pendingLeave();

        leave.reject(decider(), null);

        assertThat(leave.getNote()).isEqualTo("ORIGINAL REASON: hospital appointment");
        assertThat(leave.getDecisionNote()).isNull();
    }

    @Test
    @DisplayName("Approving records no decision note")
    void approvingLeavesTheDecisionNoteEmpty() {
        LeaveRequest leave = pendingLeave();

        leave.approve(decider());

        assertThat(leave.getNote()).isEqualTo("ORIGINAL REASON: hospital appointment");
        assertThat(leave.getDecisionNote()).isNull();
    }
}
