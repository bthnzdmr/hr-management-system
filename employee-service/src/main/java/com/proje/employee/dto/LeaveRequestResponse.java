package com.proje.employee.dto;

import com.proje.employee.entity.LeaveRequest;
import com.proje.employee.entity.LeaveStatus;
import com.proje.employee.entity.LeaveType;

import java.time.Instant;
import java.time.LocalDate;

/** Izin istegi cevabi. */
import com.proje.employee.audit.AuditDetail;
import com.proje.employee.audit.AuditLabel;

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
        String decisionNote,
        String recordedBy,
        String decidedBy,
        Instant decidedAt,
        Instant createdAt
) implements AuditLabel, AuditDetail {

    @Override
    public String auditLabel() {
        return employeeFullName;
    }

    /**
     * Talebin O ANKI hali.
     *
     * Karar bilgisi `status` icinde zaten var, bu yuzden `@Auditable`in
     * `summary` alani (approved / rejected / withdrawn) buraya EKLENMIYOR --
     * eklenseydi "approved Approved ..." diye tekrarlardi.
     */
    @Override
    public String auditDetail() {
        String span = AuditDetail.day(startDate) + " - " + AuditDetail.day(endDate);
        String dayCount = days + (days == 1 ? " day" : " days");

        return AuditDetail.humanise(status) + " · " + AuditDetail.humanise(type)
                + " leave · " + span + " (" + dayCount + ")";
    }


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
                leave.getDecisionNote(),
                leave.getCreatedBy().getEmail(),
                leave.getDecidedBy() == null ? null : leave.getDecidedBy().getEmail(),
                leave.getDecidedAt(),
                leave.getCreatedAt());
    }
}
