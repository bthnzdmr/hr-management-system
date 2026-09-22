package com.proje.notification.listener;

import com.proje.notification.event.LeaveEvent;
import com.proje.notification.event.LeaveEventType;
import com.proje.notification.service.EventClaimService;
import com.proje.notification.service.LeaveMailService;
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
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Izin bildiriminin teslim yolu.
 *
 * <p>Bu sinif uc dinleyicinin sonuncusuydu ve kapsam olcumu onu %17,2'de
 * buldu: iki kardesinin testleri vardi, bunun HIC yoktu. Ucu de ayni telafi
 * adimini ({@code releaseQuietly}) tasiyor ve o adim projede bir kez SESSIZCE
 * kaybolan mail uretmisti -- ama telafiyi tutan tek test
 * {@code AccountEventListenerTest} icindeydi.
 *
 * <p>Yani en yeni modul, kardeslerinin bedel odeyerek ogrendigi dersi
 * uygulanmis kodda tasiyor fakat TESTTE tasimiyordu: bugun 58. satirdaki
 * {@code releaseQuietly(event)} silinse hicbir sey kirilmazdi.
 */
@ExtendWith(MockitoExtension.class)
class LeaveEventListenerTest {

    private static final Long LEAVE_REQUEST_ID = 77L;
    private static final Long EMPLOYEE_ID = 42L;

    @Mock
    private LeaveMailService mailService;

    @Mock
    private EventClaimService eventClaimService;

    @InjectMocks
    private LeaveEventListener listener;

    private LeaveEvent event() {
        return event(LeaveEventType.REQUESTED);
    }

    private LeaveEvent event(LeaveEventType type) {
        return new LeaveEvent(
                UUID.randomUUID(), type, Instant.now(),
                LEAVE_REQUEST_ID, EMPLOYEE_ID, "Ada Lovelace", "ada@example.com",
                "grace@example.com",
                "ANNUAL", LocalDate.of(2027, 5, 4), LocalDate.of(2027, 5, 8), 5,
                "PENDING", "dis hekimi", null,
                EMPLOYEE_ID, "ada@example.com",
                Set.of(), Set.of());
    }

    private void claimSucceeds() {
        when(eventClaimService.claim(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Sends the mail when the event is seen for the first time")
    void sendsMailForANewEvent() {
        claimSucceeds();

        listener.onLeaveEvent(event(), null);

        verify(mailService).send(any());
    }

    @Test
    @DisplayName("Claims the event before sending, never the other way round")
    void claimsBeforeSending() {
        // Ters sirada mail gidip kayit yazilamazsa tekrar teslimde IKINCI mail
        // giderdi -- ve gonderilmis mail geri alinamaz.
        claimSucceeds();

        listener.onLeaveEvent(event(), null);

        InOrder order = inOrder(eventClaimService, mailService);
        order.verify(eventClaimService).claim(any(), any(), any());
        order.verify(mailService).send(any());
    }

    @Test
    @DisplayName("Claims the event against the leave request, not the employee")
    void claimsAgainstTheLeaveRequest() {
        // Olay HEM leaveRequestId HEM employeeId tasiyor ve ikisi farkli kimlik
        // uzaylaridir. Konu olarak personel yazilsaydi ayni kisinin iki ayri
        // izin olayi ayni konuyu paylasirdi. Bu modulde tam olarak bu tur bir
        // karistirma bir kez GERCEK oldu (hesap adresi ile personel adresi).
        claimSucceeds();

        listener.onLeaveEvent(event(), null);

        verify(eventClaimService).claim(any(), eq("REQUESTED"), eq(LEAVE_REQUEST_ID));
    }

    @Test
    @DisplayName("Sends no second mail when the same event arrives again")
    void ignoresAnAlreadyProcessedEvent() {
        // At-least-once teslim outbox tasariminin dogrudan sonucu; idempotency
        // bir iyi uygulama degil, ZORUNLULUK.
        when(eventClaimService.claim(any(), any(), any())).thenReturn(false);

        listener.onLeaveEvent(event(), null);

        verify(mailService, never()).send(any());
    }

    @Test
    @DisplayName("Treats a lost claim race as a duplicate instead of failing")
    void losingTheClaimRaceIsNotAFailure() {
        // Iki tuketici de kontrolu gecti, biri once kaydetti. Kaybeden taraf
        // birincil anahtar ihlali alir -- bu bir ARIZA degil, idempotency
        // garantisinin ta kendisi. Istisna disari sizsaydi mesaj bosuna
        // yeniden teslim edilir ve sonunda DLQ'ya duserdi.
        when(eventClaimService.claim(any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        listener.onLeaveEvent(event(), null);

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

        assertThatThrownBy(() -> listener.onLeaveEvent(event(), null))
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

        listener.onLeaveEvent(event(), null);

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

        assertThatThrownBy(() -> listener.onLeaveEvent(event(), null))
                .hasMessageContaining("smtp down");
    }

    @Test
    @DisplayName("Handles a decision the same way as a request")
    void handlesADecision() {
        // Uc olay tipi ayni yoldan gecer; aliciyi ve metni secen sey
        // LeaveMailService'tir, dinleyici degil.
        claimSucceeds();

        listener.onLeaveEvent(event(LeaveEventType.DECIDED), null);

        verify(mailService).send(any());
    }

    @Test
    @DisplayName("Handles a cancellation the same way as a request")
    void handlesACancellation() {
        claimSucceeds();

        listener.onLeaveEvent(event(LeaveEventType.CANCELLED), null);

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

        listener.onLeaveEvent(event(), "trace-42");

        assertThat(seen.get()).isEqualTo("trace-42");
    }

    @Test
    @DisplayName("Falls back to the event id when no correlation id is present")
    void fallsBackToTheEventId() {
        claimSucceeds();
        LeaveEvent event = event();

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(MDC.get("correlationId"));
            return null;
        }).when(mailService).send(any());

        listener.onLeaveEvent(event, null);

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

        assertThatThrownBy(() -> listener.onLeaveEvent(event(), "trace-42"));

        assertThat(MDC.get("correlationId")).isNull();
    }
}
