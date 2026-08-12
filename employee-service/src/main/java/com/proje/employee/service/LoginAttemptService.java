package com.proje.employee.service;

import com.proje.employee.exception.TooManyLoginAttemptsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Basarisiz giris denemelerini sayar ve esik asilinca gecici olarak reddeder.
 *
 * <p><b>Neden gerekli:</b> Uc {@code permitAll}, kilitlenme yok, gecikme yok.
 * Parola politikasi yalnizca uzunluk. Ama ikincil etki daha ciddi: BCrypt is
 * faktoru 10, yani HER DENEME ~100 ms CPU yakiyor. Saniyede 50 istek atan bir
 * betik Tomcat'in butun ipliklerini BCrypt'e bogar ve mesru istekler zaman
 * asimina ugrar -- kimlik dogrulamasi gerektirmeyen bir uctan tam hizmet reddi.
 *
 * <p>Buradaki ironi ogretici: BCrypt'in yavasligi bir GUVENLIK OZELLIGIDIR
 * (sozluk saldirisini pahali kilar) ama hiz siniri olmayan bir ucta saldirganin
 * silahina donusur -- maliyeti saldirgan degil SUNUCU oder. Bir savunmanin
 * maliyetini kimin odedigi, o savunmanin ise yarayip yaramadigi kadar onemlidir.
 *
 * <p><b>Neden hem e-posta hem IP basina?</b>
 * <ul>
 *   <li>Yalnizca e-posta basina olsaydi saldirgan parolayi sabitleyip kullanici
 *       tarar (password spraying) ve hicbir sayaci doldurmazdi.</li>
 *   <li>Yalnizca IP basina olsaydi dagitik bir saldiri sayactan kacar, ustelik
 *       NAT arkasindaki mesru kullanicilar birbirini kilitlerdi.</li>
 * </ul>
 *
 * <p><b>Neden bellekte?</b> Bu olcekte tek kopya calisiyor ve sayaclar kalici
 * olmak zorunda degil: yeniden baslatma sayaclari sifirlar, en kotu ihtimalle
 * saldirgan birkac deneme daha kazanir. Cok kopyaya gecilirse bu siniflar
 * paylasilan bir depoya (Redis) tasinmali -- aksi halde saldirgan kopya
 * sayisi kadar deneme hakki kazanir.
 */
@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    /** Sayaclarin sinirsiz buyumesini engelleyen ust sinir. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final int maxAttempts;
    private final Duration blockDuration;

    /**
     * Zaman DISARIDAN verilir.
     *
     * Instant.now() dogrudan cagrilsaydi sureye bagli davranis ancak GERCEK
     * zaman bekleyerek sinanabilirdi -- ya test yavas olur ya da saat
     * cozunurlugune bagli KARARSIZ olurdu. Ikincisi yasandi: sifir pencereli
     * bir test tek basina geciyor, tam kosuda dusuyordu.
     */
    private final Clock clock;

    private final Map<String, Attempts> byKey = new ConcurrentHashMap<>();

    // @Autowired SART: iki kurucu var ve Spring hangisini kullanacagini
    // bilemez -- isaretlenmezse varsayilan kurucu arar ve baglam ACILMAZ.
    // Birim testler bunu goremedi cunku nesneyi dogrudan kuruyorlar.
    @Autowired
    public LoginAttemptService(
            @Value("${app.login.max-attempts}") int maxAttempts,
            @Value("${app.login.block-duration-seconds}") long blockDurationSeconds) {
        this(maxAttempts, blockDurationSeconds, Clock.systemUTC());
    }

    /** Testler icin: zamani kontrol edilebilir kilar. */
    LoginAttemptService(int maxAttempts, long blockDurationSeconds, Clock clock) {
        this.maxAttempts = maxAttempts;
        this.blockDuration = Duration.ofSeconds(blockDurationSeconds);
        this.clock = clock;
    }

    /**
     * Denemeye izin var mi?
     *
     * @throws TooManyLoginAttemptsException esik asildiysa
     */
    public void assertNotBlocked(String email, String clientIp) {
        Instant now = clock.instant();

        if (isBlocked(emailKey(email), now) || isBlocked(ipKey(clientIp), now)) {
            // E-posta LOGLANIR, istemciye donmez: cevap her durumda ayni
            // kalmali, aksi halde "bu hesap kilitli" mesaji hesabin VAR
            // OLDUGUNU dogrulardi.
            log.warn("Login blocked after too many failed attempts: email={}, ip={}", email, clientIp);
            throw new TooManyLoginAttemptsException();
        }
    }

    /** Basarili giris ilgili sayaclari sifirlar. */
    public void recordSuccess(String email, String clientIp) {
        byKey.remove(emailKey(email));
        byKey.remove(ipKey(clientIp));
    }

    public void recordFailure(String email, String clientIp) {
        Instant now = clock.instant();
        // Sayac dolduysa yeni anahtar eklenmez ama MEVCUT anahtarlar artmaya
        // devam eder: aksi halde saldirgan tabloyu doldurup kendi sayacinin
        // artmasini engelleyebilirdi.
        boolean roomForNewKeys = byKey.size() < MAX_TRACKED_KEYS;

        increment(emailKey(email), now, roomForNewKeys);
        increment(ipKey(clientIp), now, roomForNewKeys);
    }

    private void increment(String key, Instant now, boolean roomForNewKeys) {
        byKey.compute(key, (ignored, current) -> {
            if (current == null) {
                return roomForNewKeys ? new Attempts(1, now) : null;
            }
            // Pencere kapandiysa sayac bastan baslar: bir gun once yapilan
            // uc hatali deneme, bugunku denemeyi engellememeli.
            if (current.isExpired(now, blockDuration)) {
                return new Attempts(1, now);
            }
            return current.increment(now);
        });
    }

    private boolean isBlocked(String key, Instant now) {
        Attempts attempts = byKey.get(key);
        if (attempts == null) {
            return false;
        }
        if (attempts.isExpired(now, blockDuration)) {
            byKey.remove(key);
            return false;
        }
        return attempts.count() >= maxAttempts;
    }

    // Ayni metin hem e-posta hem IP olarak gorunemesin diye onek konur:
    // oneksiz olsaydi "10.0.0.1" adresli bir istemci, ayni metni e-posta gibi
    // yazan birinin sayacini paylasirdi.
    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.toLowerCase(java.util.Locale.ROOT));
    }

    private static String ipKey(String clientIp) {
        return "ip:" + (clientIp == null ? "" : clientIp);
    }

    private record Attempts(int count, Instant lastAttemptAt) {

        Attempts increment(Instant now) {
            return new Attempts(count + 1, now);
        }

        boolean isExpired(Instant now, Duration window) {
            return lastAttemptAt.plus(window).isBefore(now);
        }
    }
}
