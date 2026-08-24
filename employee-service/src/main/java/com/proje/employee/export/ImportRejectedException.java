package com.proje.employee.export;

/**
 * Dosya reddedildi; hicbir sey yazilmadi.
 *
 * Istisna TASIYICI: raporu icinde goturur. Yonetici cozumu ancak kayitlar
 * yazildiktan SONRA yapilabiliyor ve oradaki bir hatanin transaction'i geri
 * alabilmesi icin firlatilmasi sart -- normal donus, yarim bir yuklemeyi
 * commit ederdi.
 *
 * Ayni ders `noRollbackFor` ve `REQUIRES_NEW` vakalarinda alinmisti: hepsi
 * ya da hicbiri sozu, transaction sinirinda tutulur.
 */
public class ImportRejectedException extends RuntimeException {

    private final transient ImportReport report;

    public ImportRejectedException(ImportReport report) {
        super("The import was rejected with " + report.errors().size() + " invalid row(s)");
        this.report = report;
    }

    public ImportReport report() {
        return report;
    }
}
