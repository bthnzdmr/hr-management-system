package com.proje.employee.entity;

/**
 * Roller IS ISLEVINE gore adlandirilir, yetkiye gore degil.
 *
 * "CAN_EDIT_EMPLOYEES" gibi bir ad, yetki degistiginde rolun adini yalanci
 * yapardi. Rol kimin ne is yaptigini soyler; o isin hangi uclara erismesi
 * gerektigi SecurityConfig'te ve servis katmaninda yazar.
 */
public enum Role {

    /** Kendi kaydini gorur. En temel seviye (employee self-service). */
    EMPLOYEE,

    /** Kendi kaydini ve dogrudan astlarini gorur. */
    MANAGER,

    /** Tum personeli gorur ve duzenler; maasa erisebilen tek insan rolu. */
    HR_SPECIALIST,

    /**
     * Hesaplari ve erisimi yonetir. Personel verisini DUZENLEYEMEZ ve maasi
     * GOREMEZ: erisimi yoneten kisinin ucret bilgisine ihtiyaci yoktur.
     * Rehberi okuyabilir, cunku hesabi personele baglamak icin kimin var
     * oldugunu bilmesi gerekir.
     */
    SYSTEM_ADMIN,

    /**
     * Makine kimligi. Notification Service bildirim hazirlarken personelin
     * yoneticisini soruyor; bunun icin rehberi okumasi gerekir ama baska
     * hicbir seye ihtiyaci yoktur (en az yetki).
     */
    SERVICE
}
