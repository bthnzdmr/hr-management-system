package com.proje.employee.event;

/**
 * Izin olaylari.
 *
 * <p>`EmployeeEventType`e EKLENEMEZ: tuketicideki `RabbitConfigTest` o enum'un
 * HER degerinin personel bildirim kuyruguna bagli olmasini iddia ediyor ve izin
 * olaylari oraya teslim edilmemeli. Ayni gerekceyle `AccountEventType` de ayri
 * bir aile olarak acilmisti.
 *
 * <p>Ayrik kuyruk bir izolasyon karari da: bozuk bir izin mail sablonu, personel
 * bildirimlerinin park kuyrugunu doldurmamali.
 *
 * <p>YENI DEGER EKLERKEN: routing key'i tuketicideki `RabbitConfig`'in baglama
 * listesine de yazilmali, yoksa olay yayinlanir ama kimseye teslim EDILMEZ.
 */
public enum LeaveEventType {

    /** Talep acildi; ilgilenen taraf onay verecek kisidir. */
    REQUESTED("leave.requested"),

    /** Talep sonuclandi (onaylandi veya reddedildi). */
    DECIDED("leave.decided"),

    /**
     * Talep geri cekildi.
     *
     * Karar DEGILDIR (`decidedBy` bos kalir) ama yine de bildirilmeye deger:
     * geri cekmeyi talebin sahibi disinda Ik ve yonetici de yapabilir. Kendi
     * cektigi talebin maili gurultudur; ayrimi tuketici `actorEmail` ile yapar.
     */
    CANCELLED("leave.cancelled");

    private final String routingKey;

    LeaveEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }
}
