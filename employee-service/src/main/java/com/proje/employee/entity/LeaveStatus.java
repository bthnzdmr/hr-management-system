package com.proje.employee.entity;

/**
 * Izin isteginin durumu.
 *
 * PENDING disindaki her durum NIHAIDIR: onaylanmis bir izin reddedilemez,
 * reddedilmis bir izin onaylanamaz. Fikir degistirmek yeni bir istek acmak
 * demektir -- boylece kararin ne zaman ve kim tarafindan verildigi tek bir
 * satirda kalir ve denetim izi bozulmaz.
 */
public enum LeaveStatus {

    /** Karar bekliyor. Cakisma kisiti bu durumu da hesaba katar. */
    PENDING,

    APPROVED,

    REJECTED,

    /** Karara varilmadan geri cekildi. */
    CANCELLED
}
