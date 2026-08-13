package com.proje.employee.exception;

/**
 * Kapatilmis bir departmana yeni personel baglanamaz.
 *
 * Pasif yonetici kuraliyla ayni mantik: mevcut baglantilar korunur, YENI
 * baglanti kurulamaz. Aksi halde sistem, kapatilmis bir departmanda calisan
 * personel uretirdi.
 */
public class InactiveDepartmentException extends RuntimeException {

    public InactiveDepartmentException(Long departmentId) {
        super("Department " + departmentId + " is closed and cannot take new employees");
    }
}
