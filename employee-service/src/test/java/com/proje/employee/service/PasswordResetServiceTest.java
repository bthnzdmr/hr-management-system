package com.proje.employee.service;

import com.proje.employee.dto.PasswordResetConfirmRequest;
import com.proje.employee.entity.PasswordResetToken;
import com.proje.employee.entity.Role;
import com.proje.employee.entity.User;
import com.proje.employee.event.AccountEvent;
import com.proje.employee.event.AccountEventType;
import com.proje.employee.event.EmployeeEvent;
import com.proje.employee.event.OutboxWriter;
import com.proje.employee.exception.InvalidPasswordResetTokenException;
import com.proje.employee.repository.PasswordResetTokenRepository;
import com.proje.employee.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2031-05-01T10:00:00Z");
    private static final String EMAIL = "ada@example.com";

    @Mock
    private PasswordResetTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private OutboxWriter outboxWriter;

    private PasswordResetService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(tokenRepository, userRepository, refreshTokenService,
                passwordEncoder, outboxWriter, 30, 120,
                Clock.fixed(NOW, ZoneOffset.UTC));

        user = new User(EMAIL, "old-hash", Set.of(Role.EMPLOYEE));
        when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
    }

    @Test
    @DisplayName("issues a token and publishes an event for an active account")
    void issuesTokenForActiveAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.request(EMAIL);

        verify(tokenRepository).save(any(PasswordResetToken.class));

        ArgumentCaptor<AccountEvent> captor = ArgumentCaptor.forClass(AccountEvent.class);
        verify(outboxWriter).write(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(AccountEventType.PASSWORD_RESET_REQUESTED);
        assertThat(captor.getValue().email()).isEqualTo(EMAIL);
        assertThat(captor.getValue().resetToken()).isNotBlank();
    }

    @Test
    @DisplayName("stays silent for an unknown email so accounts cannot be enumerated")
    void staysSilentForUnknownEmail() {
        // Istisna FIRLATILMAZ: cagirana verilen cevap her durumda ayni olmali,
        // yoksa saldirgan hangi e-postalarin hesabi oldugunu cikarirdi.
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        service.request("nobody@example.com");

        verify(tokenRepository, never()).save(any());
        verify(outboxWriter, never()).write(any(AccountEvent.class));
    }

    @Test
    @DisplayName("refuses to revive a deactivated account")
    void refusesDeactivatedAccount() {
        // Kapatilmis bir hesaba erisimi geri vermek, ayrilan personelin
        // hesabini kapatan kurali bu yoldan delerdi.
        user.setActive(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.request(EMAIL);

        verify(tokenRepository, never()).save(any());
        verify(outboxWriter, never()).write(any(AccountEvent.class));
    }

    @Test
    @DisplayName("sends no second link while a fresh one is still pending")
    void throttlesRepeatedRequests() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findLatestUnusedIssuedAt(any())).thenReturn(NOW.minusSeconds(30));

        service.request(EMAIL);

        verify(outboxWriter, never()).write(any(AccountEvent.class));
    }

    @Test
    @DisplayName("issues again once the cooldown has passed")
    void issuesAgainAfterCooldown() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(tokenRepository.findLatestUnusedIssuedAt(any())).thenReturn(NOW.minusSeconds(300));

        service.request(EMAIL);

        verify(outboxWriter).write(any(AccountEvent.class));
    }

    @Test
    @DisplayName("sets the password and closes every session")
    void confirmChangesPasswordAndRevokesSessions() {
        givenUsableToken();

        service.confirm(new PasswordResetConfirmRequest("raw-token", "a-long-enough-password"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        // Sifirlamanin amaci zaten budur: baskasinin elindeki her sey gecersiz olsun.
        verify(refreshTokenService).revokeAllFor(any(), eq("password reset"));
        verify(tokenRepository).invalidateAllFor(any(), eq(NOW));
    }

    @Test
    @DisplayName("refuses a token the database would not let us consume")
    void refusesUnusableToken() {
        // consumeIfUsable 0 donerse jeton ya kullanilmis ya suresi dolmustur;
        // ikisi de AYNI hatayi verir, yoksa gecerli jetonun varligi dogrulanirdi.
        givenUsableToken();
        when(tokenRepository.consumeIfUsable(anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.confirm(
                new PasswordResetConfirmRequest("raw-token", "a-long-enough-password")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);

        assertThat(user.getPasswordHash()).isEqualTo("old-hash");
        verify(refreshTokenService, never()).revokeAllFor(anyLong(), anyString());
    }

    @Test
    @DisplayName("refuses a token that was never issued")
    void refusesUnknownToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(
                new PasswordResetConfirmRequest("made-up", "a-long-enough-password")))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
    }

    @Test
    @DisplayName("keeps the raw token out of any textual representation")
    void neverPrintsTheToken() {
        // record'un uretilmis toString'i butun bilesenleri basar ve bu nesne
        // tam da log satirina dusmeye aday. Ayni sizinti projede bir kez
        // UserCreateRequest'te olculdu.
        AccountEvent event = new AccountEvent("id", AccountEventType.PASSWORD_RESET_REQUESTED,
                NOW, 1L, EMAIL, "super-secret-token", NOW.plusSeconds(1800));

        assertThat(event.toString()).doesNotContain("super-secret-token").contains("***");
    }

    @Test
    @DisplayName("does not publish an employee event")
    void publishesNoEmployeeEvent() {
        // Parola sifirlama bir PERSONEL olgusu degildir; personel kuyruguna
        // dusseydi tuketici onu cozemez ve DLQ'ya giderdi.
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.request(EMAIL);

        verify(outboxWriter, never()).write(any(EmployeeEvent.class));
    }

    private void givenUsableToken() {
        PasswordResetToken stored = new PasswordResetToken("hash", user, NOW, NOW.plusSeconds(1800));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(tokenRepository.consumeIfUsable(anyString(), any())).thenReturn(1);
    }
}
