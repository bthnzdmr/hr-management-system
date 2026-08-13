package com.proje.employee.exception;

/**
 * Istek gecerli ama sistemin mevcut durumu izin vermiyor.
 *
 * Ornek: icinde aktif personel olan departmani kapatmak. Mesaj istemciye
 * gosterilir -- bu bir is kuralidir, ic detay degil.
 */
public class DepartmentRuleViolationException extends RuntimeException {

    public DepartmentRuleViolationException(String message) {
        super(message);
    }
}
