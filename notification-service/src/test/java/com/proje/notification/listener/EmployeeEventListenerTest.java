package com.proje.notification.listener;

import com.proje.notification.event.EmployeeEvent;
import com.proje.notification.event.EmployeeEventType;
import com.proje.notification.service.EventClaimService;
import com.proje.notification.service.ManagerLookupService;
import com.proje.notification.service.NotificationMailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeEventListenerTest {

    @Mock
    private EventClaimService eventClaimService;

    @Mock
    private NotificationMailService mailService;

    @Mock
    private ManagerLookupService managerLookupService;

    @InjectMocks
    private EmployeeEventListener listener;

    private EmployeeEvent event(UUID eventId) {
        return new EmployeeEvent(eventId, EmployeeEventType.CREATED, Instant.now(),
                42L, "Grace", "Hopper", "grace@example.com", "Sales", "Engineer");
    }

    @Test
    @DisplayName("Sends a mail and records the event when it is seen for the first time")
    void processesNewEvent() {
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(true);

        listener.onEmployeeEvent(event(eventId), null);

        // Sahiplenme ayri bir bean'e tasindi (EventClaimService); dinleyicinin
        // sorumlulugu artik yalnizca ORKESTRASYON.
        verify(eventClaimService).claim(any());
        verify(mailService).send(any(), any());
    }

    @Test
    @DisplayName("Sends no second mail when the same event is delivered again")
    void ignoresAlreadyProcessedEvent() {
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(false);

        listener.onEmployeeEvent(event(eventId), null);

        verify(mailService, never()).send(any(), any());
    }

    @Test
    @DisplayName("Writes the record to the database before sending the mail")
    void writesRecordBeforeSendingMail() {
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(true);

        listener.onEmployeeEvent(event(eventId), null);

        // Sira SART: once sahiplen, sonra mail. Ters sirada mail gidip kayit
        // yazilamazsa tekrar teslimde IKINCI MAIL giderdi -- gonderilmis mail
        // geri alinamaz.
        InOrder order = inOrder(eventClaimService, mailService);
        order.verify(eventClaimService).claim(any());
        order.verify(mailService).send(any(), any());
    }

    @Test
    @DisplayName("Sends no mail when another consumer claimed the event first")
    void sendsNoMailWhenAnotherConsumerClaimedTheEvent() {
        // Yaris durumu: iki tuketici de existsById kontrolunu gecti, biri once
        // kaydetti. Kaybeden taraf maili HENUZ gondermemis olmalidir.
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(false);

        listener.onEmployeeEvent(event(eventId), null);

        verify(mailService, never()).send(any(), any());
    }

    @Test
    @DisplayName("Lets a mail failure propagate so the message is redelivered")
    void propagatesMailFailure() {
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(true);
        doThrow(new RuntimeException("smtp down")).when(mailService).send(any(), any());

        assertThatThrownBy(() -> listener.onEmployeeEvent(event(eventId), null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("smtp down");
    }

    @Test
    @DisplayName("Puts the correlation id from the message into the logging context")
    void adoptsCorrelationIdFromTheMessage() {
        // Uretici kimligi mesaj basligina koyuyor. Tuketici bunu kendi MDC'sine
        // almazsa iki servisin loglari birlestirilemez -- ki bu servisin bugune
        // kadar yazdigi HER satir bos kimlikle basiliyordu.
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(true);

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(MDC.get("correlationId"));
            return null;
        }).when(mailService).send(any(), any());

        listener.onEmployeeEvent(event(eventId), "abc-123");

        assertThat(seen.get()).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("Falls back to the event id when the message carries no correlation id")
    void fallsBackToEventId() {
        // V8 oncesi yazilmis satirlarda kimlik yok. eventId de uctan uca akan
        // benzersiz bir deger: "izlenemez" olmaktansa farkli bir anahtarla
        // izlenebilir olmak iyidir.
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(true);

        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(MDC.get("correlationId"));
            return null;
        }).when(mailService).send(any(), any());

        listener.onEmployeeEvent(event(eventId), null);

        assertThat(seen.get()).isEqualTo(eventId.toString());
    }

    @Test
    @DisplayName("Clears the correlation id so it cannot bleed into the next message")
    void clearsCorrelationIdAfterwards() {
        // Iplik havuzdan geliyor: temizlenmezse iki ayri isin loglari
        // birbirine karisir ve iz yanlis yere baglanir.
        UUID eventId = UUID.randomUUID();
        when(eventClaimService.claim(any())).thenReturn(true);

        listener.onEmployeeEvent(event(eventId), "abc-123");

        assertThat(MDC.get("correlationId")).isNull();
    }
}
