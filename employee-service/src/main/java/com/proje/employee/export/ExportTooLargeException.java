package com.proje.employee.export;

/**
 * Disari aktarma tavani asildi.
 *
 * Sessizce kirpmak yerine reddediliyor: eksik oldugunu SOYLEMEYEN bir dosya,
 * eksik dosyadan kotudur -- kullanici tam sanip karar verirdi. Ayni ilke izin
 * takviminde ve organizasyon haritasinda da uygulandi.
 */
public class ExportTooLargeException extends RuntimeException {

    public ExportTooLargeException(long found, int max) {
        super("This export would contain " + found + " rows, more than the limit of "
                + max + ". Narrow the search first.");
    }
}
