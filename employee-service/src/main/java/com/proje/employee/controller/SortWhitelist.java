package com.proje.employee.controller;

import com.proje.employee.exception.InvalidSortPropertyException;
import org.springframework.data.domain.Pageable;

import java.util.Set;

/**
 * Siralanabilir kolonlarin izin listesi.
 *
 * Pageable dogrudan istekten geldigi icin istemci HERHANGI bir entity alanina
 * gore siralama isteyebilir. Bu, cevapta donmeyen bir alani bile YAN KANAL
 * uzerinden okunabilir kilar: maas cevapta yok ama "?sort=salary,desc" ucret
 * siralamasini oldugu gibi verir. Ayni sekilde "?sort=passwordHash" hesaplari
 * BCrypt ozetine gore dizer.
 *
 * IZIN listesi kullanilir, YASAK listesi degil: yasak listesinde yarin eklenen
 * her hassas kolon varsayilan olarak ACIK olurdu. Guvenlikte dogru varsayilan
 * kapalidir.
 */
final class SortWhitelist {

    private SortWhitelist() {
    }

    static void check(Pageable pageable, Set<String> allowed) {
        pageable.getSort().forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(order.getProperty(), allowed);
            }
        });
    }
}
