package com.proje.employee.exception;

/**
 * Jeton gecerli ama arkasindaki hesap artik yok.
 *
 * Bu bir ISTEMCI durumudur (eskimis kimlik bilgisi), sunucu arizasi degil.
 * Onceden IllegalStateException firlatiliyor ve catch-all uzerinden 500
 * donuyordu: izleme kirlenirdi -- uyari kurulmus bir sistemde bu, olmayan bir
 * arizayi bildiren alarm demektir. Ustelik arayuzdeki interceptor 500'u
 * "tekrar denenebilir" sayip kullaniciyi cikarmiyordu.
 */
public class StaleCredentialsException extends RuntimeException {

    public StaleCredentialsException(String email) {
        super(email);
    }
}
