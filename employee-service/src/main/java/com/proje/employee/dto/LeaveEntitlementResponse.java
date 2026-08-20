package com.proje.employee.dto;

import com.proje.employee.audit.AuditDetail;
import com.proje.employee.audit.AuditLabel;
import com.proje.employee.entity.Employee;
import com.proje.employee.entity.LeaveEntitlement;

/** Kaydedilmis hak satiri. Etkisini gormek icin bakiye ucu sorulur. */
public record LeaveEntitlementResponse(
        Long employeeId,

        /**
         * Denetim izi icin de gerekli: yalnizca id tasinsaydi iz
         * "EMPLOYEE #906" derdi ve KIMIN hakkinin degistigi ancak ayri bir
         * aramayla ogrenilirdi.
         */
        String employeeFullName,

        int year,
        int entitledDays,
        int carriedOverDays,
        String note
) implements AuditLabel, AuditDetail {

    @Override
    public String auditLabel() {
        return employeeFullName;
    }

    /** Gerekce ZORUNLU oldugu icin daima yazilabilir; "neden farkli" sorusu odur. */
    @Override
    public String auditDetail() {
        return year + " entitlement: " + entitledDays + " days"
                + (carriedOverDays > 0 ? " + " + carriedOverDays + " carried over" : "")
                + (note == null || note.isBlank() ? "" : " · " + note);
    }

    public static LeaveEntitlementResponse from(LeaveEntitlement entitlement) {
        // Personel AYNI transaction'da zaten yuklu; tembel vekil birinci
        // seviye onbellekten cozuluyor, ek sorgu uretmiyor.
        Employee employee = entitlement.getEmployee();

        return new LeaveEntitlementResponse(
                employee.getId(),
                employee.getFirstName() + " " + employee.getLastName(),
                entitlement.getYear(),
                entitlement.getEntitledDays(),
                entitlement.getCarriedOverDays(),
                entitlement.getNote());
    }
}
