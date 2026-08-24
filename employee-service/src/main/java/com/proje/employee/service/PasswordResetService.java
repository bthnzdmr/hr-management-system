package com.proje.employee.service;

import com.proje.employee.dto.PasswordResetConfirmRequest;
import com.proje.employee.entity.PasswordResetToken;
import com.proje.employee.entity.User;
import com.proje.employee.event.AccountEvent;
import com.proje.employee.event.AccountEventType;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.exception.InvalidPasswordResetTokenException;
import com.proje.employee.repository.PasswordResetTokenRepository;
import com.proje.employee.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Parolasini unutan kullanicinin tek yolu.
 *
 * Yonetici parola BELIRLEYEMEZ (kayitli karar: belirleyebilseydi kullanicinin
 * kimligiyle giris yapar ve denetim kaydinda ayirt edilemezdi). Bu yuzden akis
 * kullanicinin kendi posta kutusundan gecer.
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    // 256 bit; RefreshTokenService ile ayni gerekce.
    private static final int TOKEN_BYTES = 32;

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final OutboxWriter outboxWriter;
    private final SecureRandom random = new SecureRandom();
    private final Duration validity;
    private final Duration inviteValidity;
    private final Duration resendCooldown;

    /** Zaman DISARIDAN verilir; gerekcesi LoginAttemptService'te olculdu. */
    private final Clock clock;

    // @Autowired SART: iki kurucu var ve isaretlenmezse baglam ACILMAZ.
    @Autowired
    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                RefreshTokenService refreshTokenService,
                                PasswordEncoder passwordEncoder,
                                PasswordPolicy passwordPolicy,
                                OutboxWriter outboxWriter,
                                @Value("${app.password-reset.validity-minutes}") long validityMinutes,
                                @Value("${app.password-reset.invite-validity-hours}") long inviteHours,
                                @Value("${app.password-reset.resend-cooldown-seconds}") long cooldownSeconds) {

        this(tokenRepository, userRepository, refreshTokenService, passwordEncoder,
                passwordPolicy, outboxWriter,
                validityMinutes, inviteHours, cooldownSeconds, Clock.systemUTC());
    }

    /** Testler icin: zamani kontrol edilebilir kilar. */
    PasswordResetService(PasswordResetTokenRepository tokenRepository,
                         UserRepository userRepository,
                         RefreshTokenService refreshTokenService,
                         PasswordEncoder passwordEncoder,
                         PasswordPolicy passwordPolicy,
                         OutboxWriter outboxWriter,
                         long validityMinutes,
                         long inviteHours,
                         long cooldownSeconds,
                         Clock clock) {

        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.outboxWriter = outboxWriter;
        this.validity = Duration.ofMinutes(validityMinutes);
        // Davet daha uzun omurlu: sifirlamada kullanici o an bekliyor, davette
        // ise kisi ise yeni basliyor olabilir ve maili ertesi gun acabilir.
        this.inviteValidity = Duration.ofHours(inviteHours);
        this.resendCooldown = Duration.ofSeconds(cooldownSeconds);
        this.clock = clock;
    }

    /**
     * Sifirlama baglantisi ister.
     *
     * Hesap bulunamasa da SESSIZCE doner: cagirana verilen cevap her durumda
     * ayni olmalidir, yoksa saldirgan elindeki e-posta listesini deneyip
     * hangilerinin bu sistemde hesabi oldugunu cikarirdi (numaralandirma).
     *
     * Pasif hesap da sessizce atlanir -- kapatilmis bir hesaba erisimi geri
     * vermek, ayrilan personelin hesabini kapatan kuralin delinmesi olurdu.
     */
    @Transactional
    public void request(String email) {
        Optional<User> found = userRepository.findByEmail(email);

        if (found.isEmpty() || !found.get().isActive()) {
            // Kimlik loga yazilir, cevaba degil.
            log.info("Password reset requested for an unusable account: {}", email);
            return;
        }

        User user = found.get();
        Instant now = clock.instant();

        // Hiz siniri icin ayri bir sayaca gerek yok: bekleyen jetonun yasi
        // zaten "bu hesaba yakin zamanda mail gitti mi" sorusunu cevapliyor.
        // Bu, bir posta kutusunu bombalamayi da engeller.
        Instant lastIssued = tokenRepository.findLatestUnusedIssuedAt(user.getId());
        if (lastIssued != null && lastIssued.isAfter(now.minus(resendCooldown))) {
            log.info("Password reset throttled for user {}", user.getId());
            return;
        }

        // Mail, mevcut boru hattindan gider: bu servis SMTP'yi hic tanimaz.
        issue(user, AccountEventType.PASSWORD_RESET_REQUESTED, validity);

        log.info("Password reset issued for user {}", user.getId());
    }

    /**
     * Jetonu tuketip yeni parolayi yazar.
     *
     * Hata mesaji jetonun neden gecersiz oldugunu SOYLEMEZ: "suresi dolmus" ile
     * "boyle bir jeton yok" ayrimi, gecerli bir jetonun varligini dogrulardi.
     */
    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        Instant now = clock.instant();
        String hash = hash(request.token());

        // Kullaniciyi tuketmeden ONCE okuyoruz; atomiklik guncellemeden gelir,
        // bu okumadan degil.
        PasswordResetToken stored = tokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidPasswordResetTokenException::new);

        if (tokenRepository.consumeIfUsable(hash, now) == 0) {
            throw new InvalidPasswordResetTokenException();
        }

        User user = stored.getUser();

        // Jeton TUKETILDIKTEN sonra dogrulaniyor ve bu bilincli: aksi halde
        // zayif parola denemesi jetonu harcamadan doner ve baglanti sinirsiz
        // kez denenebilirdi. Istisna transaction'i geri alir, yani jeton da
        // gecerli kalir -- kullanici ayni baglantiyla tekrar dener.
        passwordPolicy.validate(request.newPassword(), user.getEmail());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        // Ayni kullanicinin bekleyen DIGER jetonlari da duser: saldirganin daha
        // once tetikledigi bir baglanti, kurban parolasini degistirdikten sonra
        // hala calisiyor olmamali.
        tokenRepository.invalidateAllFor(user.getId(), now);

        // Parola sifirlamanin amaci zaten budur: baskasinin elindeki her sey
        // gecersiz olsun.
        refreshTokenService.revokeAllFor(user.getId(), "password reset");

        log.info("Password reset completed for user {}", user.getId());
    }

    /**
     * Yeni acilan hesaba "parolani belirle" baglantisi uretir.
     *
     * Hesap acmakla AYNI transaction'da cagrilir; hiz sinirine takilmaz cunku
     * ortada kullanici istegi degil bir yonetici islemi var ve hesap yeni.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void invite(User user) {
        issue(user, AccountEventType.INVITED, inviteValidity);
        log.info("Invite issued for user {}", user.getId());
    }

    private void issue(User user, AccountEventType type, Duration ttl) {
        Instant now = clock.instant();
        String token = randomToken();

        tokenRepository.save(new PasswordResetToken(hash(token), user, now, now.plus(ttl)));

        outboxWriter.write(new AccountEvent(
                UUID.randomUUID().toString(), type, now,
                user.getId(), user.getEmail(), token, now.plus(ttl)));
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);

        // URL-safe: jeton mail icindeki baglantida tasiniyor.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256, BCrypt degil; gerekcesi RefreshTokenService'te. */
    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));

            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
