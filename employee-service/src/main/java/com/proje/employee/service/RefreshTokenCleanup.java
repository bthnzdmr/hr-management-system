package com.proje.employee.service;

import com.proje.employee.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Suresi dolmus yenileme jetonlarini siler.
 *
 * Temizlik olmadan tablo SUREKLI buyur: her giris bir satir birakir ve hicbiri
 * silinmez. Notification Service de servis hesabiyla duzenli giris yaptigi icin
 * bu, olmasi muhtemel degil KESIN bir buyumedir.
 *
 * Ayri bir sinif: RefreshTokenService kimlik dogrulama akisindan sorumlu,
 * burasi bakim isinden. Ayni sinifta olsalardi @Scheduled bir metot, kimlik
 * dogrulama testlerinin her kosusunda zamanlayiciyi da devreye sokardi.
 */
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenCleanup(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(
            fixedDelayString = "${app.jwt.refresh-cleanup-interval-ms:3600000}",
            initialDelayString = "${app.jwt.refresh-cleanup-interval-ms:3600000}")
    @Transactional
    public void deleteExpiredTokens() {
        int removed = refreshTokenRepository.deleteExpired(Instant.now());

        // Sifir silinen her saat loglanirsa log gurultuye doner.
        if (removed > 0) {
            log.info("Removed {} expired refresh tokens", removed);
        }
    }
}
