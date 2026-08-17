package com.proje.employee.service;

import com.proje.employee.repository.PasswordResetTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Suresi dolmus sifirlama jetonlarini siler.
 *
 * RefreshTokenCleanup ile ayni gerekce: her istek bir satir birakir ve hicbiri
 * kendiliginden silinmez. Kullanilmis ama suresi dolmamis satirlar KORUNUR --
 * silinselerdi ayni baglantinin ikinci kez sunulmasi "taninmiyor" degil
 * "hic gorulmemis" gibi ele alinirdi ve tek kullanimlik olma garantisi
 * jetonun omru boyunca degil, yalnizca temizlige kadar surerdi.
 */
@Component
public class PasswordResetTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetTokenCleanup.class);

    private final PasswordResetTokenRepository tokenRepository;

    public PasswordResetTokenCleanup(PasswordResetTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Scheduled(
            fixedDelayString = "${app.password-reset.cleanup-interval-ms}",
            initialDelayString = "${app.password-reset.cleanup-interval-ms}")
    @Transactional
    public void deleteExpiredTokens() {
        int removed = tokenRepository.deleteExpired(Instant.now());

        if (removed > 0) {
            log.info("Removed {} expired password reset tokens", removed);
        }
    }
}
