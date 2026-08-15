package com.proje.employee.dto;

import com.proje.employee.entity.LeaveType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Yeni izin istegi. */
public record LeaveRequestCreateRequest(

        @NotNull(message = "Employee is required")
        Long employeeId,

        @NotNull(message = "Leave type is required")
        LeaveType type,

        @NotNull(message = "Start date is required")
        LocalDate startDate,

        @NotNull(message = "End date is required")
        LocalDate endDate,

        @Size(max = 500, message = "Note cannot be longer than 500 characters")
        String note
) {

    /** Bitis, baslangictan once olamaz; tek gunluk izin gecerlidir. */
    @AssertTrue(message = "End date cannot be before the start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
