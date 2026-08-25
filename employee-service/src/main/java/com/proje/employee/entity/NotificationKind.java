package com.proje.employee.entity;

/**
 * Kapatilabilir bildirim turleri.
 *
 * <p>KAPALI bir kume ve veritabaninda da yazili (`ck_notification_mute_kind`):
 * uygulama disindan yazilan bir satir da kurala uymak zorunda. Yeni deger
 * eklendiginde kisit da degismeli ve bir test bunu tutuyor.
 *
 * <p><b>Personel kaydi degisikligi BILEREK burada yok.</b> O mail bir
 * SEFFAFLIK sinyalidir -- "senin kaydinda biri degisiklik yapti".
 * Kapatilabilseydi, erisimi olan biri once bildirimi susturup sonra kaydi
 * degistirebilirdi. Her bildirim kapatilabilir olmak zorunda degildir.
 */
public enum NotificationKind {

    /** Astlarindan biri izin talep etti (yoneticiye gider). */
    LEAVE_REQUEST("Leave requests from my team"),

    /** Izin talebin sonuclandi (talebi acana gider). */
    LEAVE_DECISION("Decisions on my leave requests");

    private final String label;

    NotificationKind(String label) {
        this.label = label;
    }

    /**
     * Arayuzde gorunen ad.
     *
     * Genel bir buyuk-kucuk harf donusumu YETMEZDI ("Leave request" okunurdu)
     * ve metni arayuzde ayrica tutmak, ayni bilgiyi iki yerde tutmak olurdu --
     * `Role.label()` ile ayni gerekce.
     */
    public String label() {
        return label;
    }
}
