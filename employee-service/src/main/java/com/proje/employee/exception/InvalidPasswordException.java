package com.proje.employee.exception;

/**
 * Parola degistirirken verilen MEVCUT parola yanlis.
 *
 * Bilerek 401 DEGIL 400 doner: 401, arayuzdeki interceptor'a "oturum bitti" der
 * ve kullanici parolasini yanlis yazdi diye sistemden atilirdi. Burada oturum
 * gayet gecerli; hatali olan istegin govdesidir.
 */
public class InvalidPasswordException extends RuntimeException {

    public InvalidPasswordException() {
        super("Current password is incorrect");
    }
}
