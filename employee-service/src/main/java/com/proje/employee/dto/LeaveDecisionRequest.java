package com.proje.employee.dto;

import com.proje.employee.entity.LeaveStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Bir izin istegini sonuclandirma. */
public record LeaveDecisionRequest(

        @NotNull(message = "Status is required")
        LeaveStatus status,

        @Size(max = 500, message = "Note cannot be longer than 500 characters")
        String note
) {

    /** PENDING'e geri donulemez. */
    @AssertTrue(message = "A request can only be approved, rejected or cancelled")
    public boolean isFinalStatus() {
        return status == null || status != LeaveStatus.PENDING;
    }
}
