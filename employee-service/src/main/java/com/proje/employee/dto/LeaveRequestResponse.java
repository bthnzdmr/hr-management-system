package com.proje.employee.dto;

import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.LeaveType;

import java.time.Instant;
import java.time.LocalDate;

/** Izin istegi cevabi. */
 * @param endDate son gun DAHILDIR
public record LeaveRequestResponse(
        Long id,
        Long employeeId,
        String employeeFullName,
        LeaveType type,
        LeaveStatus status,
        LocalDate startDate,
        LocalDate endDate,
        long days,
        String note,
        String decidedBy,
        Instant decidedAt,
        Instant createdAt
) {

    public static LeaveRequestResponse from(LeaveRequest leave) {
        return new LeaveRequestResponse(
                leave.getId(),
                leave.getEmployee().getId(),
                leave.getEmployee().getFirstName() + " " + leave.getEmployee().getLastName(),
                leave.getType(),
                leave.getStatus(),
                leave.getStartDate(),
                leave.getEndDate(),
                // Son gun dahil oldugu icin +1. Arayuzun bu hesabi tekrar
                // yapmasi gerekseydi ayni +1 iki yerde yasardi.
                java.time.temporal.ChronoUnit.DAYS.between(leave.getStartDate(), leave.getEndDate()) + 1,
                leave.getNote(),
                leave.getDecidedBy() == null ? null : leave.getDecidedBy().getEmail(),
                leave.getDecidedAt(),
                leave.getCreatedAt());
    }
}
