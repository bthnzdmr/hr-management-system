package com.proje.employee.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/**
 * Izin olaylarinin sozlesmesi. `EmployeeEvent` gibi JSON'dur, ortak bir Java
 * modulu degil: servisler arasindaki sozlesme JSON'dur, Java sinifi degil.
 *
 * <p><b>Yuk KIME GONDERILECEGINI soylemez.</b> `employeeEmail` ve
 * `managerEmail` birer OLGUDUR ("olay aninda bunlar boyleydi"); hangisine mail
 * gidecegine tuketici karar verir. Alani `recipientEmail` diye adlandirmak
 * olayi bir EMRE cevirirdi ve projenin kurucu kurallarindan biri bunun tam
 * tersi: olaylar olgu bildirir, emir vermez.
 *
 * <p><b>Adresler yuktedir, Feign ile ARANMAZ.</b> Uretici onlari zaten ayni
 * transaction icinde elinde tutuyor. Sonradan aramak iki sey kaybettirirdi: bir
 * ag hatasi daha, ve daha onemlisi ZAMAN -- yonetici olayin yayinlanmasiyla
 * tuketilmesi arasinda degisebilir ve o zaman mail, karar anindaki yoneticiye
 * degil BUGUNKUNE giderdi. Olay bir ANIN olgusudur.
 *
 * <p>Yonetici yoksa `managerEmail` bostur ve bu bir hata degildir: organizasyon
 * tepesindeki departman baskanlarinin yoneticisi yoktur.
 */
public record LeaveEvent(

        String eventId,
        LeaveEventType eventType,
        Instant occurredAt,

        Long leaveRequestId,
        Long employeeId,
        String employeeFullName,
        String employeeEmail,
        String managerEmail,

        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        long days,

        String status,
        String note,
        String decisionNote,

        /**
         * Olayi YAPANIN personel kimligi; hesabin personel kaydi yoksa bos.
         *
         * OLCULEN KUSUR: bu ayrim once `actorEmail` ile `employeeEmail`
         * karsilastirilarak yapiliyordu ve YANLISTI -- ikisi FARKLI kimlik
         * uzaylaridir. Canli olcumde `user@example.com` hesabiyla giren kisi
         * kendi talebini geri cekti ve `ada.lovelace@demo.example.com`
         * adresine "talebiniz geri cekildi" maili GITTI. Kimlik
         * karsilastirmasi ayni uzayda yapilir.
         */
        Long actorEmployeeId,

        /**
         * Olayi YAPAN kisi: talebi acan, karar veren ya da geri ceken.
         *
         * Ayri bir `decidedBy` alani TUTULMADI: `DECIDED` olayinda ikisi her
         * zaman ayni kisi olurdu ve ayni bilgiyi iki alanda tasimak, birinin
         * zamanla otekinden ayrilmasi demektir.
         *
         * Tuketici bunu `employeeEmail` ile KARSILASTIRIR: kisinin kendi
         * yaptigi islemi kendisine bildirmek gurultudur.
         */
        String actorEmail,

        /**
         * Alicilarin olay ANINDA susturmus oldugu bildirim turleri.
         *
         * Yonetici adresiyle AYNI gerekce: olay bir ANIN olgusudur. Tuketici
         * tercihi sonradan sorsaydi, kisi olayin yayinlanmasi ile islenmesi
         * arasinda tercihini degistirdiginde mail o anki tercihe gore giderdi
         * -- oysa bildirilen sey gecmiste yasanmis bir olay.
         *
         * Alan "gonderme" DEMEZ, "susturmustu" der; kararı yine tuketici verir.
         */
        Set<String> employeeMuted,
        Set<String> managerMuted
) {
}
