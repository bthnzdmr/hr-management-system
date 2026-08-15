package com.proje.employee.entity;

/** Izin isteginin durumu. */
public enum LeaveStatus {

    /** Karar bekliyor. Cakisma kisiti bu durumu da hesaba katar. */
    PENDING,

    APPROVED,

    REJECTED,

    /** Karara varilmadan geri cekildi. */
    CANCELLED
}
