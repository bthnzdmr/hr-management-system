package com.proje.employee.exception;

/**
 * Yenileme jetonu taninmiyor, suresi dolmus veya iptal edilmis.
 *
 * Sebep AYIRT EDILMEZ: "bu jeton iptal edilmisti" demek, saldirgana elindeki
 * jetonun bir zamanlar gecerli oldugunu soylerdi. Istemciye tek bir cevap
 * doner, ayrinti loga yazilir.
 */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException(String reason) {
        super(reason);
    }
}
