package com.proje.employee.service;

import com.proje.employee.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginAttemptServiceTest {

    private static final String EMAIL = "ada@example.com";
    private static final String IP = "10.0.0.1";
    private static final int MAX = 3;
    private static final long WINDOW_SECONDS = 300;

    /** Sabit saat: sureye bagli davranis GERCEK zaman beklemeden sinanir. */
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

    private LoginAttemptService service() {
        return new LoginAttemptService(MAX, WINDOW_SECONDS, clock);
    }

    /** Testin ilerletebildigi saat. */
    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }

    private void fail(LoginAttemptService service, String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(email, ip);
        }
    }

    @Test
    @DisplayName("Lets a user through while they are still under the limit")
    void allowsAttemptsUnderTheLimit() {
        // Parolasini hatirlamaya calisan kullanici kilitlenmemeli.
        LoginAttemptService service = service();
        fail(service, EMAIL, IP, MAX - 1);

        assertThatCode(() -> service.assertNotBlocked(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Blocks once the limit is reached")
    void blocksAtTheLimit() {
        LoginAttemptService service = service();
        fail(service, EMAIL, IP, MAX);

        assertThatThrownBy(() -> service.assertNotBlocked(EMAIL, IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("Never reveals whether the account exists")
    void messageDoesNotConfirmAccountExistence() {
        // "Bu hesap kilitli" demek hesabin VAR OLDUGUNU dogrular ve saldirgana
        // gecerli e-posta listesi cikarma imkani verirdi.
        LoginAttemptService service = service();
        fail(service, EMAIL, IP, MAX);

        assertThatThrownBy(() -> service.assertNotBlocked(EMAIL, IP))
                .hasMessageNotContaining(EMAIL)
                .hasMessageNotContaining("locked")
                .hasMessage("Too many attempts. Try again later.");
    }

    @Test
    @DisplayName("Stops password spraying: a shared address is counted too")
    void countsPerAddressNotOnlyPerAccount() {
        // Saldirgan parolayi sabitleyip KULLANICI tarayabilir; her hesapta tek
        // deneme yaparsa hicbir e-posta sayacini doldurmaz. IP sayaci bunu yakalar.
        LoginAttemptService service = service();
        service.recordFailure("a@example.com", IP);
        service.recordFailure("b@example.com", IP);
        service.recordFailure("c@example.com", IP);

        assertThatThrownBy(() -> service.assertNotBlocked("d@example.com", IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("Stops distributed guessing: the account is counted too")
    void countsPerAccountNotOnlyPerAddress() {
        // Dagitik bir saldiri her denemeyi baska adresten yapar ve IP sayacindan
        // kacar. E-posta sayaci bunu yakalar.
        LoginAttemptService service = service();
        service.recordFailure(EMAIL, "10.0.0.1");
        service.recordFailure(EMAIL, "10.0.0.2");
        service.recordFailure(EMAIL, "10.0.0.3");

        assertThatThrownBy(() -> service.assertNotBlocked(EMAIL, "10.0.0.4"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("A successful sign-in clears the counters")
    void successResetsCounters() {
        // Iki hatali denemeden sonra dogru parolayi giren kullanici, bir sonraki
        // hatasinda hemen kilitlenmemeli.
        LoginAttemptService service = service();
        fail(service, EMAIL, IP, MAX - 1);

        service.recordSuccess(EMAIL, IP);
        service.recordFailure(EMAIL, IP);

        assertThatCode(() -> service.assertNotBlocked(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The counter fades so an old mistake cannot lock anyone out")
    void counterExpires() {
        // ONCEKI HALI KARARSIZDI: pencere sifir verilip gercek saate
        // guveniliyordu ve "lastAttemptAt.isBefore(now)" ayni milisaniyede
        // false donuyordu -- test tek basina geciyor, tam kosuda dusuyordu.
        // Saat artik disaridan veriliyor ve ILERLETILIYOR.
        LoginAttemptService service = service();
        fail(service, EMAIL, IP, MAX);

        assertThatThrownBy(() -> service.assertNotBlocked(EMAIL, IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);

        clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));

        assertThatCode(() -> service.assertNotBlocked(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Stays blocked while the window is still open")
    void staysBlockedInsideTheWindow() {
        LoginAttemptService service = service();
        fail(service, EMAIL, IP, MAX);

        clock.advance(Duration.ofSeconds(WINDOW_SECONDS - 1));

        assertThatThrownBy(() -> service.assertNotBlocked(EMAIL, IP))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("Different accounts from different addresses do not block each other")
    void countersAreIndependent() {
        LoginAttemptService service = service();
        fail(service, EMAIL, IP, MAX);

        assertThatCode(() -> service.assertNotBlocked("other@example.com", "10.0.0.9"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An address cannot borrow an account's counter by imitating it")
    void addressAndAccountKeysDoNotCollide() {
        // Onek olmasaydi "10.0.0.1" adresli istemci, ayni metni e-posta gibi
        // yazan birinin sayacini paylasirdi.
        LoginAttemptService service = service();
        fail(service, "10.0.0.1", "10.0.0.2", MAX - 1);

        assertThatCode(() -> service.assertNotBlocked("10.0.0.2", "10.0.0.1"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Treats the same address as one, whatever the letter case of the account")
    void accountKeyIsCaseInsensitive() {
        // Sunucu e-postayi kucuk harfe indirger; sayac da indirgemezse
        // "Ada@..." ve "ada@..." ayri sayilir ve sinir iki katina cikardi.
        LoginAttemptService service = service();
        fail(service, "ADA@example.com", IP, MAX);

        assertThatThrownBy(() -> service.assertNotBlocked("ada@example.com", "10.0.0.9"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }
}
