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
    EMPLOYEE("Employee"),

    /** Kendi kaydini ve dogrudan astlarini gorur. */
    MANAGER("Manager"),

    /** Tum personeli gorur ve duzenler; ucrete DOKUNAMAZ. */
    HR_SPECIALIST("HR specialist"),

    /**
     * Ucret bilgisini okur ve yazar. Personel kaydi ACAMAZ: ayni kisinin hem
     * kayit acip hem ucret atamasi, sahte personel olusturmanin klasik yolu.
     */
    PAYROLL_SPECIALIST("Payroll specialist"),

    /**
     * Hesaplari ve erisimi yonetir. Personel verisini DUZENLEYEMEZ ve maasi
     * GOREMEZ: erisimi yoneten kisinin ucret bilgisine ihtiyaci yoktur.
     * Rehberi okuyabilir, cunku hesabi personele baglamak icin kimin var
     * oldugunu bilmesi gerekir.
     */
    SYSTEM_ADMIN("System administrator"),

    /**
     * Makine kimligi. Notification Service bildirim hazirlarken personelin
     * yoneticisini soruyor; bunun icin rehberi okumasi gerekir ama baska
     * hicbir seye ihtiyaci yoktur (en az yetki).
     */
    SERVICE("Service account");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /**
     * Denetim izine YAZILAN ad.
     *
     * Genel bir buyuk-kucuk harf donusumu yetmiyor: `HR_SPECIALIST` boyle
     * "Hr specialist" olurdu ve kisaltma bozulurdu.
     *
     * Ad kayit ANINDA ize yaziliyor ve orada donuyor -- `AuditLabel`daki
     * kararin aynisi. Arayuzdeki `ROLE_LABELS` ise CANLI veri icindir; ikisi
     * ayni metinleri tasiyor ve degistirilirse birlikte degistirilmeli.
     */
    public String label() {
        return label;
    }
}
