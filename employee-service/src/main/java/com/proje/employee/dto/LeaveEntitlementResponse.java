package com.proje.employee.dto;

import com.proje.employee.entity.LeaveEntitlement;

/** Kaydedilmis hak satiri. Etkisini gormek icin bakiye ucu sorulur. */
public record LeaveEntitlementResponse(
        Long employeeId,
        int year,
        int entitledDays,
        int carriedOverDays,
        String note
) {

    public static LeaveEntitlementResponse from(LeaveEntitlement entitlement) {
        return new LeaveEntitlementResponse(
                entitlement.getEmployee().getId(),
                entitlement.getYear(),
                entitlement.getEntitledDays(),
                entitlement.getCarriedOverDays(),
                entitlement.getNote());
    }
}
