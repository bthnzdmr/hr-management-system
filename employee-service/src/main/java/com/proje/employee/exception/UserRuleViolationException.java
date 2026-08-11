package com.proje.employee.exception;

/**
 * Istek gecerli ama sistemin mevcut durumu izin vermiyor.
 *
 * Ornekler: son aktif yoneticiyi dusurmek, kendi hesabini pasiflestirmek.
 * Mesaj istemciye gosterilir -- bunlar is kurallaridir, ic detay degil.
 */
public class UserRuleViolationException extends RuntimeException {

    public UserRuleViolationException(String message) {
        super(message);
    }
}
