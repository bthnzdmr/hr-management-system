package com.proje.employee.exception;

/**
 * Cok fazla basarisiz giris denemesi yapildi.
 *
 * Mesaj sabittir ve hesabin var olup olmadigina dair HICBIR SEY soylemez:
 * "bu hesap kilitli" demek, hesabin varligini dogrulardi ve saldirgana
 * gecerli e-posta listesi cikarma imkani verirdi.
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException() {
        super("Too many attempts. Try again later.");
    }
}
