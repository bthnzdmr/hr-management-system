package com.proje.notification.service;

import com.proje.notification.event.LeaveEvent;
import com.proje.notification.event.LeaveEventType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Izin bildirimi KIME gider?
 *
 * <p>Yonlendirme kararinin tuketicide olmasi bilincli: olay yalnizca olgulari
 * tasir, "suna gonder" demez.
 */
class LeaveMailRoutingTest {

    private JavaMailSender mailSender;
    private MeterRegistry registry;
    private LeaveMailService service;

    private static final Long ADA = 218L;
    private static final Long GRACE = 212L;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage())
                .thenAnswer(call -> new MimeMessage(jakarta.mail.Session.getInstance(new Properties())));

        registry = new SimpleMeterRegistry();
        service = new LeaveMailService(mailSender, "hr@company.test", registry);
    }

    private LeaveEvent event(LeaveEventType type, Long actorEmployeeId, String actorEmail,
                             String managerEmail) {
        return new LeaveEvent(
                java.util.UUID.randomUUID(), type, Instant.now(),
                7L, ADA, "Ada Lovelace", "ada.lovelace@company.test", managerEmail,
                "ANNUAL", LocalDate.of(2032, 3, 10), LocalDate.of(2032, 3, 15), 6,
                "PENDING", "dentist", null, actorEmployeeId, actorEmail);
    }

    private String recipientOfSentMail() throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue().getAllRecipients()[0].toString();
    }

    private double outcome(String name) {
        return registry.get("hr.leave.notification").tag("outcome", name).counter().count();
    }

    @Test
    @DisplayName("Sends a new request to the manager, who is the one who must decide")
    void requestGoesToTheManager() throws Exception {
        service.send(event(LeaveEventType.REQUESTED, ADA, "user@accounts.test",
                "grace.hopper@company.test"));

        assertThat(recipientOfSentMail()).isEqualTo("grace.hopper@company.test");
        assertThat(outcome("sent")).isEqualTo(1);
    }

    @Test
    @DisplayName("Sends a decision to the person who asked for the leave")
    void decisionGoesToTheEmployee() throws Exception {
        service.send(event(LeaveEventType.DECIDED, GRACE, "grace@accounts.test", null));

        assertThat(recipientOfSentMail()).isEqualTo("ada.lovelace@company.test");
    }

    /**
     * OLCULEN KUSUR ve bu testin var olma sebebi.
     *
     * <p>Karsilastirma once `actorEmail` ile `employeeEmail` arasindaydi ve
     * ikisi FARKLI kimlik uzaylaridir: biri HESAP adresi, digeri PERSONEL
     * adresi. Canli olcumde `user@example.com` hesabiyla giren kisi kendi
     * talebini geri cekti ve kendi islemi icin mail ALDI.
     */
    @Test
    @DisplayName("Stays quiet when people withdraw their own request, even under a different account address")
    void selfWithdrawalSendsNothing() {
        service.send(event(LeaveEventType.CANCELLED, ADA, "user@accounts.test", null));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(outcome("self_action")).isEqualTo(1);
        assertThat(outcome("sent")).isZero();
    }

    @Test
    @DisplayName("Tells the person when somebody else withdraws their request")
    void withdrawalByAnotherPersonIsAnnounced() throws Exception {
        service.send(event(LeaveEventType.CANCELLED, GRACE, "grace@accounts.test", null));

        assertThat(recipientOfSentMail()).isEqualTo("ada.lovelace@company.test");
    }

    @Test
    @DisplayName("Counts a request with no manager instead of failing")
    void requestWithoutAManagerIsCounted() {
        // Yoneticisi olmayan personel bir VERI HATASI degil: organizasyonun
        // tepesindeki departman baskanlarinin yoneticisi yoktur.
        service.send(event(LeaveEventType.REQUESTED, ADA, "user@accounts.test", null));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(outcome("no_recipient")).isEqualTo(1);
    }

    @Test
    @DisplayName("Registers every outcome at startup, before anything happens")
    void everyOutcomeExistsFromTheStart() {
        // Dokunulmamis bir seri Prometheus'ta HIC gorunmez ve ona dayanan bir
        // uyari kurali sessizce hicbir zaman atesLENMEZ. Bu tuzak projede iki
        // kez yasandi (DLQ gostergesi ve gecikme paneli).
        assertThat(outcome("sent")).isZero();
        assertThat(outcome("no_recipient")).isZero();
        assertThat(outcome("self_action")).isZero();
    }
}
