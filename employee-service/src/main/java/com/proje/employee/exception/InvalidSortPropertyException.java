package com.proje.employee.exception;

import java.util.Set;
import java.util.TreeSet;

/**
 * Istemci, siralamasina izin verilmeyen bir alan istedi.
 *
 * Istemci hatasidir, sunucu hatasi degil: 400 doner. Mesajda izin verilen
 * alanlar sayilir -- bunlar zaten cevapta gorunen alanlar, gizli bir bilgi
 * degil. Reddedilen alanin ADI mesaja KONMAZ: "salary siralanamaz" demek,
 * boyle bir alanin var oldugunu dogrulardi.
 */
public class InvalidSortPropertyException extends RuntimeException {

    private final String property;

    public InvalidSortPropertyException(String property, Set<String> allowed) {
        super("Sorting is not allowed on this field. Allowed fields: "
                + String.join(", ", new TreeSet<>(allowed)));
        this.property = property;
    }

    /** Loglanir, istemciye donmez. */
    public String getProperty() {
        return property;
    }
}
