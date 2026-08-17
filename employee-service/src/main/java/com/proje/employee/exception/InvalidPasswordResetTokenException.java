package com.proje.employee.exception;

/**
 * Jeton taninmiyor, suresi dolmus ya da zaten kullanilmis.
 *
 * UCU BIRDEN ayni istisnadir ve bu bilincli: hangisi oldugunu soylemek gecerli
 * bir jetonun varligini dogrulardi.
 */
public class InvalidPasswordResetTokenException extends RuntimeException {

    public InvalidPasswordResetTokenException() {
        super("Password reset link is invalid or has expired");
    }
}
