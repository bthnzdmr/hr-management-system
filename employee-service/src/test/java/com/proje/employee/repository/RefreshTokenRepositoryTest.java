package com.proje.employee.repository;

import com.proje.employee.entity.RefreshToken;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Gercek veritabanina karsi calisir: bu sorgularin degeri tam da veritabaninin
 * verdigi garantidedir, sahte bir depo hicbirini sinamaz.
 *
 * "docker compose up -d" gerektirir.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User owner;

    @BeforeEach
    void setUp() {
        // Onceki durumdan bagimsiz baslamak icin.
        refreshTokenRepository.deleteAllInBatch();
        owner = userRepository.save(
                new User("token.owner." + System.nanoTime() + "@example.com", "hash", User.rolesOf(Role.EMPLOYEE)));
    }

    private RefreshToken store(String hash, Instant expiresAt) {
        return refreshTokenRepository.save(
                new RefreshToken(hash, owner, Instant.now(), expiresAt));
    }

    private Instant future() {
        return Instant.now().plus(7, ChronoUnit.DAYS);
    }

    @Test
    @DisplayName("Claims an active token exactly once")
    void claimsActiveTokenOnce() {
        // Iki es zamanli yenilemenin ayni jetonu gecerli gormesi imkansiz
        // olmalidir: kosul ve yazma tek ifadede.
        store("hash-a", future());

        assertThat(refreshTokenRepository.revokeIfActive("hash-a", Instant.now())).isEqualTo(1);
        // Ikinci cagri kaybeder; bu, tekrar kullanim sinyalinin ta kendisidir.
        assertThat(refreshTokenRepository.revokeIfActive("hash-a", Instant.now())).isZero();
    }

    @Test
    @DisplayName("Reports nothing claimed for a token that does not exist")
    void claimsNothingForUnknownToken() {
        assertThat(refreshTokenRepository.revokeIfActive("never-issued", Instant.now())).isZero();
    }

    @Test
    @DisplayName("Revokes every active token of the user and leaves other users alone")
    void revokesAllForUser() {
        store("hash-a", future());
        store("hash-b", future());

        User other = userRepository.save(new User("other." + System.nanoTime() + "@example.com",
                "hash", User.rolesOf(Role.EMPLOYEE)));
        refreshTokenRepository.save(new RefreshToken("hash-c", other, Instant.now(), future()));

        assertThat(refreshTokenRepository.revokeAllForUser(owner.getId(), Instant.now())).isEqualTo(2);

        entityManager.clear();
        assertThat(refreshTokenRepository.findByTokenHash("hash-c").orElseThrow().isRevoked())
                .isFalse();
    }

    @Test
    @DisplayName("Deletes expired tokens but keeps revoked ones that can still prove reuse")
    void deletesOnlyExpiredTokens() {
        // Iptal edilmis ama suresi dolmamis satir KORUNUR: tekrar kullanim
        // tespiti onun varligina dayanir. Silinseydi replay edilen bir jeton
        // "taninmiyor" gorunur ve tum oturumlari kapatma tepkisi hic calismazdi.
        store("expired", Instant.now().minusSeconds(1));
        store("revoked-but-valid", future());
        refreshTokenRepository.revokeIfActive("revoked-but-valid", Instant.now());
        store("active", future());

        assertThat(refreshTokenRepository.deleteExpired(Instant.now())).isEqualTo(1);

        entityManager.clear();
        assertThat(refreshTokenRepository.findByTokenHash("expired")).isEmpty();
        assertThat(refreshTokenRepository.findByTokenHash("revoked-but-valid")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash("active")).isPresent();
    }

    @Test
    @DisplayName("Refuses to store two tokens with the same hash")
    void enforcesUniqueHash() {
        // Benzersizlik uygulama kodunda degil, veritabani kisitinda garanti edilir.
        store("duplicate", future());
        entityManager.flush();

        RefreshToken second = new RefreshToken("duplicate", owner, Instant.now(), future());

        assertThatThrownBy(() -> {
            refreshTokenRepository.save(second);
            entityManager.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
