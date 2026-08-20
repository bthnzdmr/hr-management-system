package com.proje.employee.dto;

import com.proje.employee.audit.AuditDetail;
import com.proje.employee.entity.LeaveEntitlement;

/** Kaydedilmis hak satiri. Etkisini gormek icin bakiye ucu sorulur. */
public record LeaveEntitlementResponse(
        Long employeeId,
        int year,
        int entitledDays,
        int carriedOverDays,
        String note
) implements AuditDetail {

    /** Gerekce ZORUNLU oldugu icin daima yazilabilir; "neden farkli" sorusu odur. */
    @Override
    public String auditDetail() {
        return year + " entitlement: " + entitledDays + " days"
                + (carriedOverDays > 0 ? " + " + carriedOverDays + " carried over" : "")
                + (note == null || note.isBlank() ? "" : " · " + note);
    }

    public static LeaveEntitlementResponse from(LeaveEntitlement entitlement) {
        return new LeaveEntitlementResponse(
                entitlement.getEmployee().getId(),
                entitlement.getYear(),
                entitlement.getEntitledDays(),
                entitlement.getCarriedOverDays(),
                entitlement.getNote());
    }
}
