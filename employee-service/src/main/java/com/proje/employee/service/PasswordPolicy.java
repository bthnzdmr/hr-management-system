package com.proje.employee.service;

import com.proje.employee.exception.WeakPasswordException;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parolanin kabul edilebilir olup olmadigina karar veren TEK yer.
 *
 * <p>Kural daha once IKI DTO'da `@Size` anotasyonu olarak yasiyordu ve ikisi de
 * AYNI hatayi tasiyordu (asagiya bakin). Bu, projenin defalarca yakaladigi
 * kalibin en kotu hali: tekrar yalnizca bakim yuku degil, KUSURU da cogaltir.
 * Parolanin secildigi her yol buradan gecer.
 */
@Component
public class PasswordPolicy {

    /** Kisa parola tahmin edilebilir; uzunluk tek basina en guclu olcuttur. */
    static final int MIN_LENGTH = 12;

    /**
     * BCrypt'in KESIN siniri: 72 BAYT.
     *
     * <p>OLCULDU ve onceki kural yanlisti: `@Size(max = 72)` KARAKTER sayiyor,
     * BCrypt ise BAYT. 72 karakterlik Turkce bir parola 144 bayt eder, yani
     * dogrulamadan gecip `BCryptPasswordEncoder.encode` icinde
     * `IllegalArgumentException` firlatiyordu. Canli olcumde sonuc **500**'du --
     * kesinlikle bir ISTEMCI hatasinin sunucu arizasi olarak donmesi.
     */
    static final int MAX_BYTES = 72;

    /** Ardisik kac karakter "dizi" sayilir (abcdef, 123456). */
    private static final int SEQUENCE_RUN = 6;

    /** E-posta yerel kismindan kac karakteri aramaya deger. */
    private static final int CONTEXT_MIN_LENGTH = 3;

    /**
     * Taban kelimeler. TAM ESLESME LISTESI DEGIL -- ve bu bir olcumun sonucu.
     *
     * <p>Klasik "en yaygin parolalar" listesinin 67 ornegi tarandi: 12 karakter
     * sinirini yalnizca IKISI gecebiliyor (%3). Yani hazir bir top-10.000
     * listesi gomseydik neredeyse tamami OLU KOD olurdu. Hayatta kalan iki
     * ornegin ikisinde de bir taban kelime GOMULUYDU
     * (`passwordpassword`, `qwertyuiop123`).
     *
     * <p>Bu yuzden dogru kodlama alt dizge aramasidir: uzunluk sinirini gecmek
     * icin kullanicinin yaptigi sey zaten yaygin bir kelimeyi UZATMAKTIR.
     */
    private static final Set<String> BASE_WORDS = Set.of(
            "password", "passwort", "parola", "sifre",
            "qwerty", "asdf", "zxcv", "azerty",
            "iloveyou", "letmein", "welcome", "monkey", "dragon", "sunshine",
            "princess", "football", "baseball", "superman", "starwars",
            "admin", "root", "login", "secret", "master", "changeme",
            "employee", "company", "hrmanagement");

    /**
     * Parolayi dogrular; ihlal varsa {@link WeakPasswordException} firlatir.
     *
     * @param password kullanicinin sectigi parola
     * @param email    hesabin e-postasi; parola kendi adresini icermemeli
     */
    public void validate(String password, String email) {
        List<String> problems = collectProblems(password, email);

        if (!problems.isEmpty()) {
            throw new WeakPasswordException(problems);
        }
    }

    /**
     * Butun ihlaller TEK SEFERDE toplanir.
     *
     * <p>Ilk ihlalde durulsaydi kullanici parolasini adim adim duzeltmek icin
     * defalarca tur atardi -- ayni gerekce CSV ice aktarmada da yazili.
     */
    private List<String> collectProblems(String password, String email) {
        List<String> problems = new ArrayList<>();

        if (password == null || password.isEmpty()) {
            problems.add("Password is required");
            return problems;
        }

        if (password.length() < MIN_LENGTH) {
            problems.add("Password must be at least " + MIN_LENGTH + " characters");
        }

        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_BYTES) {
            // Bayt sayisi YAZILIR: kullanici 40 karakter yazip "72 sinirini asiyor"
            // dediginde sebebi anlayamazdi. Aksanli ve Turkce harfler iki bayttir.
            problems.add("Password must be at most " + MAX_BYTES
                    + " bytes; this one is " + bytes + " (non-English letters take two bytes each)");
        }

        String lower = password.toLowerCase(Locale.ROOT);

        BASE_WORDS.stream()
                .filter(lower::contains)
                .findFirst()
                .ifPresent(word -> problems.add(
                        "Password must not contain the common word \"" + word + "\""));

        if (isSingleRepeatedCharacter(password)) {
            problems.add("Password must not be a single repeated character");
        }

        if (hasLongRun(lower)) {
            problems.add("Password must not contain a run of "
                    + SEQUENCE_RUN + " sequential characters");
        }

        localPartOf(email)
                .filter(local -> local.length() >= CONTEXT_MIN_LENGTH && lower.contains(local))
                .ifPresent(local -> problems.add("Password must not contain your email address"));

        return problems;
    }

    private boolean isSingleRepeatedCharacter(String password) {
        return password.chars().distinct().count() == 1;
    }

    /**
     * Artan veya azalan ardisik bir kosu var mi (abcdef, 654321)?
     *
     * <p>Kod noktasi uzerinden yurunur, char uzerinden degil: BMP disindaki bir
     * karakter iki `char`a bolunur ve ikisi arasindaki fark ardisik SANILIRDI.
     */
    private boolean hasLongRun(String value) {
        int[] points = value.codePoints().toArray();
        int run = 1;
        int direction = 0;

        for (int i = 1; i < points.length; i++) {
            int step = points[i] - points[i - 1];

            if ((step == 1 || step == -1) && (run == 1 || step == direction)) {
                direction = step;
                run++;
                if (run >= SEQUENCE_RUN) {
                    return true;
                }
            } else {
                run = 1;
                direction = 0;
            }
        }

        return false;
    }

    private java.util.Optional<String> localPartOf(String email) {
        if (email == null) {
            return java.util.Optional.empty();
        }

        int at = email.indexOf('@');
        String local = at < 0 ? email : email.substring(0, at);

        return local.isBlank()
                ? java.util.Optional.empty()
                : java.util.Optional.of(local.toLowerCase(Locale.ROOT));
    }
}
