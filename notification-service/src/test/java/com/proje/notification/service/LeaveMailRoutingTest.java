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
import java.util.Set;

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
        return event(type, actorEmployeeId, actorEmail, managerEmail, Set.of(), Set.of());
    }

    private LeaveEvent event(LeaveEventType type, Long actorEmployeeId, String actorEmail,
                             String managerEmail, Set<String> employeeMuted,
                             Set<String> managerMuted) {
        return new LeaveEvent(
                java.util.UUID.randomUUID(), type, Instant.now(),
                7L, ADA, "Ada Lovelace", "ada.lovelace@company.test", managerEmail,
                "ANNUAL", LocalDate.of(2032, 3, 10), LocalDate.of(2032, 3, 15), 6,
                "PENDING", "dentist", null, actorEmployeeId, actorEmail,
                employeeMuted, managerMuted);
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
        assertThat(outcome("muted")).isZero();
    }
    @Test
    @DisplayName("Stays quiet when the manager muted leave requests")
    void mutedManagerGetsNoRequestMail() {
        service.send(event(LeaveEventType.REQUESTED, ADA, "user@accounts.test",
                "grace.hopper@company.test", Set.of(), Set.of("LEAVE_REQUEST")));

        verify(mailSender, never()).send(any(MimeMessage.class));
        // AYRI bir sonuc: "kisi istemedi" ile "alici yok" ayni sey degil ve
        // ikisi de disaridan mail gitmemesi olarak gorunur.
        assertThat(outcome("muted")).isEqualTo(1);
        assertThat(outcome("no_recipient")).isZero();
    }

    @Test
    @DisplayName("Reads the preference of whoever receives the mail, not of the other person")
    void readsTheRecipientsOwnPreference() throws Exception {
        // Talep maili YONETICIYE gider; personelin susturmasi burada gecersiz.
        // Yanlis kisinin tercihine bakmak, susturmayi BASKASI adina uygulamak
        // olurdu -- ve bu, sessizce kaybolan bir mail demektir.
        service.send(event(LeaveEventType.REQUESTED, ADA, "user@accounts.test",
                "grace.hopper@company.test",
                Set.of("LEAVE_REQUEST", "LEAVE_DECISION"), Set.of()));

        assertThat(recipientOfSentMail()).isEqualTo("grace.hopper@company.test");
        assertThat(outcome("muted")).isZero();
    }

    @Test
    @DisplayName("Stays quiet when the employee muted decisions on their own requests")
    void mutedEmployeeGetsNoDecisionMail() {
        service.send(event(LeaveEventType.DECIDED, GRACE, "grace@accounts.test", null,
                Set.of("LEAVE_DECISION"), Set.of()));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(outcome("muted")).isEqualTo(1);
    }

    @Test
    @DisplayName("Muting decisions does not mute requests")
    void mutingOneKindLeavesTheOther() throws Exception {
        // Kirilabilirlik: kural "herhangi bir susturma varsa gonderme" olsaydi
        // bu test duser ve tek bir tercih butun bildirimleri kapatirdi.
        service.send(event(LeaveEventType.REQUESTED, ADA, "user@accounts.test",
                "grace.hopper@company.test", Set.of(), Set.of("LEAVE_DECISION")));

        assertThat(recipientOfSentMail()).isEqualTo("grace.hopper@company.test");
    }

    @Test
    @DisplayName("Treats an unknown preference name as no preference at all")
    void unknownPreferenceNameIsHarmless() throws Exception {
        // Uretici yeni bir tur eklediginde bu servis KIRILMAMALI: karsilastirma
        // metin uzerinden ve taninmayan bir ad hicbir seyi susturmaz. Enum
        // olarak cozulseydi butun olay ayristirilamaz olurdu.
        service.send(event(LeaveEventType.REQUESTED, ADA, "user@accounts.test",
                "grace.hopper@company.test", Set.of(), Set.of("SOMETHING_NEW")));

        assertThat(recipientOfSentMail()).isEqualTo("grace.hopper@company.test");
    }

    @Test
    @DisplayName("Tolerates an event published before preferences existed")
    void nullPreferenceSetsAreTolerated() throws Exception {
        // Outbox'ta bekleyen ESKI bir olayda bu alanlar hic yoktur ve `null`
        // gelir; alan eklemek geriye donuk uyumlu olmali.
        service.send(event(LeaveEventType.REQUESTED, ADA, "user@accounts.test",
                "grace.hopper@company.test", null, null));

        assertThat(recipientOfSentMail()).isEqualTo("grace.hopper@company.test");
    }
}
