package com.proje.employee.service;

import com.proje.employee.entity.RefreshToken;
import com.proje.employee.entity.User;
import com.proje.employee.exception.InvalidRefreshTokenException;
import com.proje.employee.repository.RefreshTokenRepository;
import com.proje.employee.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    // 256 bit. Tahmin edilebilirlik riski yok: bir jetonu bulmak icin 2^256
    // denemek gerekir.
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom random = new SecureRandom();
    private final Duration validity;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               @Value("${app.jwt.refresh-validity-days:7}") long validityDays) {

        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.validity = Duration.ofDays(validityDays);
    }

    /** Yeni bir yenileme jetonu uretir. Donen deger DUZ METINDIR ve bir daha okunamaz. */
    @Transactional
    public String issue(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown user: " + email));

        return issueFor(user);
    }

    /**
     * Eski jetonu iptal edip yenisini verir (rotation).
     *
     * Her yenilemede jeton degistigi icin calinan bir jetonun omru, mesru
     * istemcinin bir sonraki yenilemesine kadardir.
     */
    /**
     * noRollbackFor sart.
     *
     * Tekrar kullanim tespit edildiginde once tum oturumlar iptal edilir, sonra
     * istisna firlatilir. Varsayilan davranista RuntimeException transaction'i
     * geri alir ve GUVENLIK TEPKISI de silinirdi: jetonlar kagit uzerinde iptal
     * edilir, veritabaninda gecerli kalirdi. Uctan uca kosuda yakalandi --
     * sahte nesnelerle calisan birim test geri almayi goremez.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Rotation rotate(String presentedToken) {
        String hash = hash(presentedToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is not recognised"));

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidRefreshTokenException("Refresh token has expired");
        }

        // Hesap kapatilmissa oturum da bitmelidir.
        //
        // Olculdu: bu kontrol olmadan pasiflestirilen bir hesap giris YAPAMIYOR
        // ama elindeki jetonla oturumunu SURESIZ yeniliyordu. Yani "hesabi kapat"
        // islemi, isten ayrilmis birine karsi hicbir sey yapmiyordu. Iptal
        // mekanizmasi vardi; kararin sahibine sormayi unutmustuk.
        if (!stored.getUser().isActive()) {
            revokeAllFor(stored.getUser().getId(), "account is disabled");
            throw new InvalidRefreshTokenException("Account is disabled");
        }

        // Atomik kapma: kosul ve yazma tek ifadede. 0 donmesi jetonun ZATEN
        // iptal edilmis oldugunu soyler.
        if (refreshTokenRepository.revokeIfActive(hash, Instant.now()) == 0) {
            // Iptal edilmis bir jeton yeniden sunuldu. Mesru istemci onu
            // atmisti; demek ki bir kopyasi dolasiyor. Hangi tarafin saldirgan
            // oldugunu bilemeyiz, bu yuzden TUM oturumlar kapatilir.
            revokeAllFor(stored.getUser().getId(), "token reuse detected");
            throw new InvalidRefreshTokenException("Refresh token was already used");
        }

        User user = stored.getUser();
        return new Rotation(user.getEmail(), user.getRole().name(), issueFor(user));
    }

    /**
     * Bir kullanicinin butun oturumlarini kapatir.
     *
     * Parola degisiminde ve hesap pasiflestirmede de cagrilir: ikisi de
     * "bu kimlikle acilmis her sey artik gecersiz" demektir.
     */
    @Transactional
    public int revokeAllFor(Long userId, String reason) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());

        // Kullanici id'si loglanir, jeton DEGIL: gecerli bir jeton dogrudan
        // kimlik bilgisidir ve loga girmemelidir.
        if (revoked > 0) {
            log.warn("Revoked {} active refresh tokens for user {}: {}", revoked, userId, reason);
        }
        return revoked;
    }

    /** Cikista cagrilir. Bilinmeyen jeton sessizce yok sayilir: cikis her zaman basarilidir. */
    @Transactional
    public void revoke(String presentedToken) {
        refreshTokenRepository.revokeIfActive(hash(presentedToken), Instant.now());
    }

    private String issueFor(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);

        // URL-safe: jeton JSON govdesinde tasiniyor ama loglarda ve adres
        // cubugunda bozulmamasi icin '+' ve '/' kullanilmaz.
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();

        refreshTokenRepository.save(new RefreshToken(hash(token), user, now, now.plus(validity)));
        return token;
    }

    /**
     * SHA-256, BCrypt degil.
     *
     * BCrypt parolalar icin kasitli olarak yavastir; amaci sozluk saldirisini
     * pahali kilmaktir. Buradaki jeton 256 bit RASTGELE oldugu icin sozluk
     * saldirisi diye bir sey yoktur ve her yenilemede BCrypt odemek, JWT'ye
     * gecerken kurtuldugumuz maliyeti geri getirirdi.
     */
    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 her JVM'de zorunludur; buraya dusulmesi kurulumun bozuk
            // oldugu anlamina gelir.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public record Rotation(String email, String role, String refreshToken) {
    }
}
