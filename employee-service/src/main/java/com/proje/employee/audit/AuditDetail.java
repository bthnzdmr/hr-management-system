package com.proje.employee.audit;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Denetim izinde okunacak CUMLE.
 *
 * `AuditLabel`in kardesi ve ayni gerekceyle acik bir arayuz: hangi cevabin
 * cumle urettigi derleyici tarafindan gorulur.
 *
 * <p><b>Neden ISTEK degil CEVAP?</b> Cevap SONUCU bilir. `EmployeeCreateRequest`
 * yalnizca `departmentId=1` tasir; `EmployeeResponse` departmanin ADINI tasir.
 * Denetim izini okuyan kisi "Department: 1" ile hicbir sey yapamaz.
 *
 * <p><b>Neden sunucuda uretiliyor?</b> Onceden arayuzde 145 satirlik bir
 * ayristirici, Java'nin urettigi `Type[a=1, b=2]` bicimini tersine cozuyordu.
 * Iki kusuru vardi ve ikisi de okuma aninda KAPATILAMAZ:
 * <ul>
 *   <li>Ic makine kaydediliyordu -- ekranda
 *       "kind: ALL · Employee: null · allSalaries: false" gorunuyordu ve orada
 *       hicbir bilgi yok.</li>
 *   <li>Isim yerine id kaydediliyordu; ad veride hic bulunmuyordu.</li>
 * </ul>
 * Ustelik ayristirici, sunucudaki alan adlarina yazili olmayan bir bagimlilik
 * kuruyordu: bir alan yeniden adlandirildiginda ekran SESSIZCE bozulurdu.
 */
public interface AuditDetail {

    /**
     * Bir cumle. Sir icermez ve VIRGULLE ayrilmis alan listesi DEGILDIR.
     *
     * Ornek: "Hired as Compiler Engineer in Software Development".
     */
    String auditDetail();

    /**
     * Tarihler ekranin geri kalaniyla AYNI bicimde yazilir (`gg-aa-yyyy`).
     *
     * ISO daha "sunucu isi" gorunurdu ama bu metin dogrudan bir insana
     * gosteriliyor ve ayni ekranda iki farkli tarih bicimi olmamali. Yerel
     * ayara bakilmiyor: makineye gore degisen bir bicim test edilemez --
     * ayni karar ucret ve tarih bicimlendirmesinde iki kez verilmisti.
     */
    DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    static String day(LocalDate date) {
        return date == null ? "" : DAY.format(date);
    }

    /**
     * "RESIGNED" -> "Resigned"; "END_OF_CONTRACT" -> "End of contract".
     *
     * `Locale.ROOT` SART. Yerel ayara birakilsaydi Turkce bir makinede `I`
     * harfi noktasiz `i`ye donerdi: "Resigned" yerine "Resigned" degil
     * **"Resigned"in bozulmus hali** yazilirdi. OLCULDU -- uc test bu yuzden
     * dustu. Ayni ilke ucret ve tarih bicimlendirmesinde de uygulanmisti:
     * makineye gore degisen bir cikti test edilemez.
     */
    static String humanise(Enum<?> value) {
        if (value == null) {
            return "";
        }

        String words = value.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');

        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
