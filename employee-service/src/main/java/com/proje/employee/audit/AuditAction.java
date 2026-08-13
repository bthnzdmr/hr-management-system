package com.proje.employee.audit;

/**
 * Denetim izine yazilan is turleri.
 *
 * Kapali bir enum: serbest metin olsaydi "ROLES_CHANGED", "roles_changed" ve
 * "RoleChange" ayri seyler sayilir ve "bu kullanici kac kez rol degistirdi"
 * sorusu cevaplanamazdi. Ayni gerekce TerminationReason'da da gecerliydi.
 */
public enum AuditAction {

    /** Yeni giris hesabi acildi. */
    ACCOUNT_CREATED,

    /** Hesabin rolleri degistirildi -- gorevler ayriligini ilgilendiren tek islem. */
    ROLES_CHANGED,

    /** Hesap acildi veya kapatildi. */
    ACCOUNT_STATUS_CHANGED,

    /** Personel ise alindi. */
    EMPLOYEE_CREATED,

    /** Personel ayrildi veya yeniden ise alindi. */
    EMPLOYEE_STATUS_CHANGED,

    /** Ucret bilgisi degisti -- degerin KENDISI kayda yazilmaz. */
    SALARY_CHANGED,

    /** Yeni departman acildi. */
    DEPARTMENT_CREATED,

    /** Departman acildi veya kapatildi. */
    DEPARTMENT_STATUS_CHANGED
}
