package com.proje.employee.exception;

import java.util.List;

/**
 * Secilen parola politikayi karsilamiyor.
 *
 * <p>Ayri bir istisna, `InvalidPasswordException`la ayni sey DEGIL: o
 * "mevcut parolayi yanlis yazdin" der, bu ise "yeni parola kabul edilemez".
 * Ikisini birlestirmek, kullaniciya yanlis seyi duzelttirirdi.
 */
public class WeakPasswordException extends RuntimeException {

    private final transient List<String> problems;

    public WeakPasswordException(List<String> problems) {
        // Butun gerekceler TEK cumlede birlestirilir. Ayri bir `errors` dizisi
        // dusunuldu ve ELENDI: `ProblemDetail.errors` bu kod tabaninda zaten
        // IKI farkli sekil tasiyor (dogrulama `field`/`message`, ice aktarma
        // `line`/`reason`) ve ucuncu bir sekil, arayuzde bir kez OLCULEN
        // "undefined: undefined" hatasinin tekrari icin acik davetiye olurdu.
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    /** Ihlallerin listesi; mesaj bunlarin birlesimidir. */
    public List<String> getProblems() {
        return problems;
    }
}
