package com.proje.employee.exception;

/**
 * Kayit, istemci onu okuduktan SONRA baskasi tarafindan degistirildi.
 *
 * Kapatilan kusur kayip guncelleme: guncelleme butun alanlari istekten yazdigi
 * icin, bayat bir formla kaydetmek digerinin degisikligini sessizce geri
 * aliyordu -- ve kimse fark etmiyordu.
 *
 * 409 doner, 400 degil: istegin KENDISI gecerli, sistemin durumu degismis.
 * Ayni ayrim son yonetici kuralinda ve mukerrer izin talebinde de yapiliyor.
 */
public class StaleRecordException extends RuntimeException {

    public StaleRecordException(String what, Long id) {
        super("This " + what + " was changed by someone else while you were editing it"
                + " (id " + id + "). Reload and try again.");
    }
}
