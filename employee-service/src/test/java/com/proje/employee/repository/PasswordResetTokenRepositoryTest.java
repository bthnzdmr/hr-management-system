package com.proje.employee.repository;

import com.proje.employee.entity.PasswordResetToken;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tek kullanimlik olma garantisi KODDA degil, tek bir UPDATE ifadesindedir.
 * Taklit edilmis bir depo bunu asla kanitlayamaz.
 *
 * "docker compose up -d" gerektirir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PasswordResetTokenRepositoryTest {

    @Autowired
    private PasswordResetTokenRepository tokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private Instant now;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAllInBatch();

        now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        owner = userRepository.save(new User(
                "reset-" + System.nanoTime() + "@example.com", "hash", Set.of(Role.EMPLOYEE)));
    }

    @Test
    @DisplayName("lets exactly one caller consume a token")
    void consumesOnlyOnce() {
        save("only-once", now.plus(30, ChronoUnit.MINUTES));

        assertThat(tokenRepository.consumeIfUsable("only-once", now)).isEqualTo(1);
        // Ikinci cagri kaybeder. "Once oku, gecerli mi bak, sonra kullan"
        // yazilsaydi ayni baglantiyi iki kez acan biri iki kez parola
        // belirleyebilirdi.
        assertThat(tokenRepository.consumeIfUsable("only-once", now)).isZero();
    }

    @Test
    @DisplayName("refuses a token that has already expired")
    void refusesExpiredToken() {
        save("stale", now.minus(1, ChronoUnit.MINUTES));

        assertThat(tokenRepository.consumeIfUsable("stale", now)).isZero();
    }

    @Test
    @DisplayName("drops the other pending tokens of the same user")
    void invalidatesSiblings() {
        save("first", now.plus(30, ChronoUnit.MINUTES));
        save("second", now.plus(30, ChronoUnit.MINUTES));

        assertThat(tokenRepository.invalidateAllFor(owner.getId(), now)).isEqualTo(2);
        assertThat(tokenRepository.consumeIfUsable("first", now)).isZero();
        assertThat(tokenRepository.consumeIfUsable("second", now)).isZero();
    }

    @Test
    @DisplayName("reports the newest pending token so a resend can be throttled")
    void reportsNewestPendingToken() {
        save("older", now.plus(30, ChronoUnit.MINUTES), now.minus(10, ChronoUnit.MINUTES));
        save("newer", now.plus(30, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES));

        assertThat(tokenRepository.findLatestUnusedIssuedAt(owner.getId()))
                .isEqualTo(now.minus(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("ignores consumed tokens when reporting the newest pending one")
    void ignoresConsumedTokensWhenThrottling() {
        // Kullanilmis bir jeton yeni istegi engellememelidir: kullanici
        // baglantiyi kullandiktan sonra yenisini isteyebilmeli.
        save("used", now.plus(30, ChronoUnit.MINUTES), now.minus(1, ChronoUnit.MINUTES));
        tokenRepository.consumeIfUsable("used", now);
        entityManager.clear();

        assertThat(tokenRepository.findLatestUnusedIssuedAt(owner.getId())).isNull();
    }

    @Test
    @DisplayName("deletes expired rows but keeps the ones still usable")
    void deletesOnlyExpiredRows() {
        save("dead", now.minus(1, ChronoUnit.MINUTES));
        save("alive", now.plus(30, ChronoUnit.MINUTES));

        assertThat(tokenRepository.deleteExpired(now)).isEqualTo(1);
        entityManager.clear();
        assertThat(tokenRepository.findByTokenHash("alive")).isPresent();
        assertThat(tokenRepository.findByTokenHash("dead")).isEmpty();
    }

    private void save(String hash, Instant expiresAt) {
        save(hash, expiresAt, now);
    }

    private void save(String hash, Instant expiresAt, Instant createdAt) {
        tokenRepository.save(new PasswordResetToken(hash, owner, createdAt, expiresAt));
        entityManager.flush();
    }
}
