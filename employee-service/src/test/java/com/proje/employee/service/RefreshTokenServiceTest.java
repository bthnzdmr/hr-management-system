package com.proje.employee.service;

import com.proje.employee.entity.RefreshToken;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.exception.InvalidRefreshTokenException;
import com.proje.employee.repository.RefreshTokenRepository;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final long VALIDITY_DAYS = 7;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private RefreshTokenService service() {
        return new RefreshTokenService(refreshTokenRepository, userRepository, VALIDITY_DAYS);
    }

    private User user(Long id, String email) {
        User user = new User(email, "hash", Role.USER);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    /** Servisin sakladigi ozetin ayni algoritmayla uretildigini bagimsizca dogrular. */
    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private RefreshToken storedToken(User owner, String plaintext, Instant expiresAt) {
        return new RefreshToken(sha256(plaintext), owner, Instant.now(), expiresAt);
    }

    private RefreshToken captureSaved() {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        return saved.getValue();
    }

    @Test
    @DisplayName("Stores only the hash of the issued token, never the token itself")
    void storesOnlyTheHash() {
        // Veritabani yedegini okuyan biri oturumlari devralabilmemeli.
        when(userRepository.findByEmail("ada@example.com"))
                .thenReturn(Optional.of(user(1L, "ada@example.com")));

        String issued = service().issue("ada@example.com");

        assertThat(captureSaved().getTokenHash())
                .isNotEqualTo(issued)
                .isEqualTo(sha256(issued));
    }

    @Test
    @DisplayName("Issues a token that expires after the configured number of days")
    void issuesTokenWithConfiguredExpiry() {
        when(userRepository.findByEmail("ada@example.com"))
                .thenReturn(Optional.of(user(1L, "ada@example.com")));

        service().issue("ada@example.com");

        RefreshToken saved = captureSaved();
        assertThat(Duration.between(saved.getIssuedAt(), saved.getExpiresAt()).toDays())
                .isEqualTo(VALIDITY_DAYS);
    }

    @Test
    @DisplayName("Issues a different token on every call")
    void issuesUnpredictableTokens() {
        when(userRepository.findByEmail("ada@example.com"))
                .thenReturn(Optional.of(user(1L, "ada@example.com")));

        RefreshTokenService service = service();

        assertThat(service.issue("ada@example.com"))
                .isNotEqualTo(service.issue("ada@example.com"));
    }

    @Test
    @DisplayName("Revokes the presented token and returns a different one")
    void rotatesTheToken() {
        User ada = user(1L, "ada@example.com");
        String presented = "old-token";

        when(refreshTokenRepository.findByTokenHash(sha256(presented)))
                .thenReturn(Optional.of(storedToken(ada, presented, Instant.now().plusSeconds(600))));
        when(refreshTokenRepository.revokeIfActive(anyString(), any())).thenReturn(1);

        RefreshTokenService.Rotation rotation = service().rotate(presented);

        assertThat(rotation.email()).isEqualTo("ada@example.com");
        assertThat(rotation.role()).isEqualTo("USER");
        // Yeni jeton eskisiyle ayni olsaydi dondurmenin hicbir anlami kalmazdi.
        assertThat(rotation.refreshToken()).isNotEqualTo(presented);
        verify(refreshTokenRepository).revokeIfActive(eq(sha256(presented)), any());
    }

    @Test
    @DisplayName("Rejects a token that is not recognised")
    void rejectsUnknownToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().rotate("made-up"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Rejects an expired token without issuing a new one")
    void rejectsExpiredToken() {
        String presented = "stale";

        when(refreshTokenRepository.findByTokenHash(sha256(presented)))
                .thenReturn(Optional.of(storedToken(
                        user(1L, "ada@example.com"), presented, Instant.now().minusSeconds(1))));

        assertThatThrownBy(() -> service().rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository, never()).revokeIfActive(anyString(), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Revokes every session when an already used token is presented again")
    void revokesEverythingOnReuse() {
        // Mesru istemci jetonu attiktan sonra tekrar sunulmasi, bir KOPYASININ
        // dolastigi anlamina gelir. Hangi tarafin saldirgan oldugu bilinemez,
        // bu yuzden butun oturumlar kapatilir.
        String presented = "already-used";

        when(refreshTokenRepository.findByTokenHash(sha256(presented)))
                .thenReturn(Optional.of(storedToken(
                        user(7L, "ada@example.com"), presented, Instant.now().plusSeconds(600))));
        // 0 = satiri baska biri onceden kapatmis.
        when(refreshTokenRepository.revokeIfActive(anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service().rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        verify(refreshTokenRepository).revokeAllForUser(eq(7L), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Refuses to refresh the session of a deactivated account")
    void refusesDeactivatedAccount() {
        // Olculdu: bu kontrol olmadan pasiflestirilen hesap giris yapamiyor ama
        // elindeki jetonla oturumunu SURESIZ yeniliyordu.
        User dismissed = user(3L, "gone@example.com");
        dismissed.setActive(false);
        String presented = "still-held";

        when(refreshTokenRepository.findByTokenHash(sha256(presented)))
                .thenReturn(Optional.of(storedToken(
                        dismissed, presented, Instant.now().plusSeconds(600))));

        assertThatThrownBy(() -> service().rotate(presented))
                .isInstanceOf(InvalidRefreshTokenException.class);

        // Yalnizca reddedilmez, kalan oturumlar da kapatilir.
        verify(refreshTokenRepository).revokeAllForUser(eq(3L), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("Signing out revokes the presented token")
    void revokesOnLogout() {
        service().revoke("some-token");

        verify(refreshTokenRepository).revokeIfActive(eq(sha256("some-token")), any());
    }

    @Test
    @DisplayName("Signing out with an unknown token is not an error")
    void logoutIsForgiving() {
        // Cikis her zaman basarilidir: kullaniciyi "cikamadin" diye tutmak
        // sacma olurdu ve jetonun taninmamasi zaten istenen son durumdur.
        when(refreshTokenRepository.revokeIfActive(anyString(), any())).thenReturn(0);

        service().revoke("never-existed");
    }
}
