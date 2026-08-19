package com.proje.notification.listener;

import com.proje.notification.event.AccountEvent;
import com.proje.notification.event.AccountEventType;
import com.proje.notification.service.EventClaimService;
import com.proje.notification.service.PasswordResetMailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sifirlama ve davet mailinin teslim yolu.
 *
 * <p>Kapsam olcumu bu sinifi %15,5'te buldu -- kod tabanindaki en dusuk deger.
 * Kardesi {@code EmployeeEventListener}'in testleri vardi, bu yoktu; oysa
 * ikisinin arasinda kritik bir fark var: burada bir <b>telafi adimi</b>
 * bulunuyor ({@code releaseQuietly}) ve o adim projede bir kez SESSIZCE
 * kaybolan mail uretmisti.
 *
 * <p>Kaybin bedeli de asimetrik: personel bildirimi kaybolursa kimse maili
 * gormez; davet maili kaybolursa <b>hesap kalici olarak girilemez kalir</b> --
 * yonetici parola belirleyemedigi icin baska bir yol yok.
 */
@ExtendWith(MockitoExtension.class)
class AccountEventListenerTest {

    @Mock
    private PasswordResetMailService mailService;

    @Mock
    private EventClaimService eventClaimService;

    @InjectMocks
    private AccountEventListener listener;

    private AccountEvent event() {
        return event(AccountEventType.PASSWORD_RESET_REQUESTED);
    }

    private AccountEvent event(AccountEventType type) {
        return new AccountEvent(UUID.randomUUID(), type, Instant.now(),
                42L, "ada@example.com", "raw-reset-token",
                Instant.now().plusSeconds(1800));
    }

    private void claimSucceeds() {
        when(eventClaimService.claim(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Sends the mail when the event is seen for the first time")
    void sendsMailForANewEvent() {
        claimSucceeds();

        listener.onAccountEvent(event(), null);

        verify(mailService).send(any());
    }

    @Test
    @DisplayName("Claims the event before sending, never the other way round")
    void claimsBeforeSending() {
        // Ters sirada mail gidip kayit yazilamazsa tekrar teslimde IKINCI mail
        // giderdi -- ve gonderilmis mail geri alinamaz.
        claimSucceeds();

        listener.onAccountEvent(event(), null);

        InOrder order = inOrder(eventClaimService, mailService);
        order.verify(eventClaimService).claim(any(), any(), any());
        order.verify(mailService).send(any());
    }

    @Test
    @DisplayName("Sends no second mail when the same event arrives again")
    void ignoresAnAlreadyProcessedEvent() {
        // At-least-once teslim outbox tasariminin dogrudan sonucu; idempotency
        // bir iyi uygulama degil, ZORUNLULUK.
        when(eventClaimService.claim(any(), any(), any())).thenReturn(false);

        listener.onAccountEvent(event(), null);

        verify(mailService, never()).send(any());
    }

    @Test
    @DisplayName("Treats a lost claim race as a duplicate instead of failing")
    void losingTheClaimRaceIsNotAFailure() {
        // Iki tuketici de existsById kontrolunu gecti, biri once kaydetti.
        // Kaybeden taraf birincil anahtar ihlali alir -- bu bir ARIZA degil,
        // idempotency garantisinin ta kendisi. Istisna disari sizsaydi mesaj
        // bosuna yeniden teslim edilir ve sonunda DLQ'ya duserdi.
        when(eventClaimService.claim(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        listener.onAccountEvent(event(), null);

        verify(mailService, never()).send(any());
    }

    @Test
    @DisplayName("Releases the claim when the mail fails, so the retry can deliver it")
    void releasesTheClaimWhenTheMailFails() {
        // OLCULMUS HATA. Sahiplenme KENDI transaction'inda commit edilir.
        // Telafi olmasaydi yeniden teslim "zaten islendi" der ve ACK'lerdi:
        // mail kaybolur, DLQ'ya bile dusmezdi.
        claimSucceeds();
        doThrow(new RuntimeException("smtp down")).when(mailService).send(any());

        assertThatThrownBy(() -> listener.onAccountEvent(event(), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("smtp down");

        verify(eventClaimService).release(any());
    }

    @Test
    @DisplayName("Keeps the claim when the mail succeeds")
    void keepsTheClaimOnSuccess() {
        // Telafinin diger yarisi: basarili teslimden sonra sahiplenme
        // BIRAKILMAMALI, yoksa mukerrer mail engeli ortadan kalkar.
        claimSucceeds();

        listener.onAccountEvent(event(), null);

        verify(eventClaimService, never()).release(any());
    }

    @Test
    @DisplayName("Reports the original mail failure even when the release also fails")
    void theOriginalFailureSurvivesAFailedRelease() {
        // Telafi kendi hatasini YUTAR ama ozgun istisnanin yerine gecmez:
        // gecseydi loglarda "release edilemedi" gorunur, mailin neden
        // gitmedigi ise KAYBOLURDU.
        claimSucceeds();
        doThrow(new RuntimeException("smtp down")).when(mailService).send(any());
        doThrow(new RuntimeException("database gone")).when(eventClaimService).release(any());

        assertThatThrownBy(() -> listener.onAccountEvent(event(), null))
                .hasMessageContaining("smtp down");
    }

    @Test
    @DisplayName("Handles an invitation the same way as a reset")
    void handlesAnInvitation() {
        // Iki olay tipi ayni yoldan gecer; ayiran sey yalnizca mail metnidir.
        // Davet kaybolursa hesap kalici olarak girilemez kalir.
        claimSucceeds();

        listener.onAccountEvent(event(AccountEventType.INVITED), null);

        verify(mailService).send(any());
    }

    @Test
    @DisplayName("Adopts the correlation id carried by the message")
    void adoptsTheCorrelationId() {
        claimSucceeds();

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(MDC.get("correlationId"));
            return null;
        }).when(mailService).send(any());

        listener.onAccountEvent(event(), "trace-42");

        assertThat(seen.get()).isEqualTo("trace-42");
    }

    @Test
    @DisplayName("Falls back to the event id when no correlation id is present")
    void fallsBackToTheEventId() {
        claimSucceeds();
        AccountEvent event = event();

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(MDC.get("correlationId"));
            return null;
        }).when(mailService).send(any());

        listener.onAccountEvent(event, null);

        assertThat(seen.get()).isEqualTo(event.eventId().toString());
    }

    @Test
    @DisplayName("Clears the correlation id even when the delivery fails")
    void clearsTheCorrelationIdAfterAFailure() {
        // Iplik havuzdan geliyor. Yalnizca basarili yolda temizlenseydi
        // basarisiz bir teslim kimligi ipligin uzerinde BIRAKIR ve sonraki
        // isin loglari yanlis ize baglanirdi.
        claimSucceeds();
        doThrow(new RuntimeException("smtp down")).when(mailService).send(any());

        assertThatThrownBy(() -> listener.onAccountEvent(event(), "trace-42"));

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    @DisplayName("The reset token never appears in the event's textual form")
    void theResetTokenIsNotInTheTextualForm() {
        // Bu satir tam olarak bir seyi koruyor: bir gun buraya
        // log.error("...", event) yazilirsa jeton loga DUSMEZ. Ayni sizinti
        // projede bir kez UserCreateRequest'te GERCEK oldu.
        assertThat(event().toString())
                .contains("***")
                .doesNotContain("raw-reset-token");
    }
}
