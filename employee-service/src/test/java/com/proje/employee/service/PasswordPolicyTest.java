package com.proje.employee.service;

import com.proje.employee.exception.WeakPasswordException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    private static final String EMAIL = "ada.lovelace@example.com";

    @ParameterizedTest
    @ValueSource(strings = {
            "quiet-harbour-lantern",
            "sekiz kedi ucar gece",
            "Tr0ubad0ur&3-anchor",
    })
    @DisplayName("Accepts a long passphrase without demanding symbols or digits")
    void acceptsLongPassphrases(String password) {
        // Buyuk harf / rakam / sembol zorunlulugu KASITLI olarak yok:
        // NIST SP 800-63B bunu acikca onermiyor, cunku pratikte "Password1!"
        // gibi tahmin edilebilir kaliplara itiyor.
        assertThatCode(() -> policy.validate(password, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rejects a password shorter than the minimum")
    void rejectsShortPasswords() {
        assertThatThrownBy(() -> policy.validate("short-one", EMAIL))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("at least " + PasswordPolicy.MIN_LENGTH);
    }

    /**
     * OLCULEN KUSUR: sinir BAYT, karakter degil.
     *
     * <p>Onceki kural iki DTO'da `@Size(max = 72)` idi ve KARAKTER sayiyordu.
     * 72 karakterlik Turkce bir parola 144 bayt eder: dogrulamadan gecer,
     * BCrypt reddeder ve kullanici **500** gorurdu.
     */
    @Test
    @DisplayName("Rejects a password over the BCrypt byte ceiling even when the character count fits")
    void rejectsPasswordsOverTheByteCeiling() {
        String turkish = "ÇĞİÖŞÜçğıöşü".repeat(6);

        assertThat(turkish.length()).isEqualTo(72);
        assertThat(turkish.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(PasswordPolicy.MAX_BYTES);

        assertThatThrownBy(() -> policy.validate(turkish, EMAIL))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("bytes");
    }

    /**
     * Politikanin gecirdigi HICBIR parola BCrypt'i patlatmamali.
     *
     * <p>Iki sinir birbirinden bagimsiz yazilirsa biri zamanla otekinden ayrilir;
     * bu test ikisini birbirine baglar ve gercek encoder'i calistirir.
     */
    @Test
    @DisplayName("Anything the policy accepts can actually be hashed")
    void acceptedPasswordsCanBeHashed() {
        var encoder = new BCryptPasswordEncoder();
        String atTheLimit = "ÇĞ".repeat(17) + "abcd";   // 38 karakter, 72 bayt

        assertThat(atTheLimit.getBytes(StandardCharsets.UTF_8).length)
                .isEqualTo(PasswordPolicy.MAX_BYTES);

        policy.validate(atTheLimit, EMAIL);

        assertThatCode(() -> encoder.encode(atTheLimit)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "mypasswordhere",          // gomulu taban kelime
            "qwertyuiop1234",
            "letmein-please-now",
    })
    @DisplayName("Rejects a password that embeds a common base word")
    void rejectsEmbeddedBaseWords(String password) {
        // Tam eslesme listesi DEGIL: 12 karakter siniri klasik listenin %97'sini
        // zaten olduruyor, geriye kalanlar ise yaygin bir kelimeyi UZATIYOR.
        assertThatThrownBy(() -> policy.validate(password, EMAIL))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("common word");
    }

    @Test
    @DisplayName("Rejects a single repeated character")
    void rejectsRepeatedCharacter() {
        assertThatThrownBy(() -> policy.validate("aaaaaaaaaaaaaa", EMAIL))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("repeated character");
    }

    @ParameterizedTest
    @ValueSource(strings = {"zebra-abcdefg-hut", "kite-987654-moon"})
    @DisplayName("Rejects a run of sequential characters")
    void rejectsSequentialRuns(String password) {
        assertThatThrownBy(() -> policy.validate(password, EMAIL))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("sequential");
    }

    @Test
    @DisplayName("Accepts a short sequence that stays under the run limit")
    void acceptsShortSequences() {
        // Kirilabilirlik: kural "herhangi bir artan cift" olsaydi bu da duserdi
        // ve neredeyse her parola reddedilirdi.
        assertThatCode(() -> policy.validate("zebra-abcd-hut-oyun", EMAIL))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rejects a password containing the account's own email name")
    void rejectsOwnEmail() {
        assertThatThrownBy(() -> policy.validate("ada.lovelace-rocks", EMAIL))
                .isInstanceOf(WeakPasswordException.class)
                .hasMessageContaining("email address");
    }

    @Test
    @DisplayName("Reports every violation at once")
    void reportsEveryViolationAtOnce() {
        // Ilk ihlalde durulsaydi kullanici parolasini adim adim duzeltmek icin
        // defalarca tur atardi -- ayni gerekce CSV ice aktarmada da yazili.
        assertThatThrownBy(() -> policy.validate("admin", EMAIL))
                .isInstanceOf(WeakPasswordException.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.throwable(WeakPasswordException.class))
                .extracting(WeakPasswordException::getProblems)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.list(String.class))
                .hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("Treats a missing email as no context rather than failing")
    void toleratesMissingEmail() {
        // Servis hesabi gibi baglami olmayan yollarda parola yine dogrulanabilmeli.
        assertThatCode(() -> policy.validate("quiet-harbour-lantern", null))
                .doesNotThrowAnyException();
    }
}
